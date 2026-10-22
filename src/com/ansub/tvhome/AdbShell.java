package com.ansub.tvhome;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;

// Runs a shell command through the TV's own adb daemon (the "network debugging" on port 5555), the way
// `adb shell <command>` does from a computer. That is the only way an app without system rights can
// restart the TV. The first time, the TV asks to allow this app; "always allow" makes it silent.
// No Android classes are used here, so the protocol can be tested on any computer.
final class AdbShell {
    private AdbShell() {}

    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f;
    private static final int VERSION = 0x01000001, MAX_DATA = 4096;
    // Prefix of a SHA-1 DigestInfo: adbd checks the signature of the 20-byte token as if it were a SHA-1 hash.
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02,
            0x1a, 0x05, 0x00, 0x04, 0x14};

    static KeyPair newKey() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    // Connects, authorizes with the key (waiting up to waitMs for the person to accept on the TV), and
    // starts "shell:<command>". Returns once the command was sent; throws if it could not be.
    static void run(String host, int port, PrivateKey priv, PublicKey pub, String command, int waitMs) throws Exception {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), 3000);
            s.setSoTimeout(waitMs);
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            OutputStream out = s.getOutputStream();
            send(out, CNXN, VERSION, MAX_DATA, "host::\0".getBytes("UTF-8"));
            int tokens = 0;
            while (true) {
                Msg m = read(in);
                if (m.cmd == CNXN) break;
                if (m.cmd != AUTH || m.arg0 != 1) throw new IOException("resposta inesperada da TV");
                tokens++;
                if (tokens == 1) send(out, AUTH, 2, 0, sign(priv, m.data));      // a key the TV may know already
                else if (tokens == 2) send(out, AUTH, 3, 0, publicKeyBlob(pub)); // ask the TV to accept this key
                else throw new IOException("autorização negada na TV");
            }
            send(out, OPEN, 1, 0, ("shell:" + command + "\0").getBytes("UTF-8"));
            s.setSoTimeout(3000);
            try { while (true) read(in); } catch (IOException ignored) {} // the TV may go down right away
        } finally {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private static final class Msg { int cmd, arg0, arg1; byte[] data; }

    private static Msg read(DataInputStream in) throws IOException {
        byte[] h = new byte[24];
        in.readFully(h);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int len = b.getInt();
        b.getInt(); // data checksum: not checked, like adbd since version 0x01000001
        int magic = b.getInt();
        if (magic != (m.cmd ^ 0xffffffff)) throw new IOException("mensagem inválida");
        if (len < 0 || len > 1024 * 1024) throw new IOException("mensagem grande demais");
        m.data = new byte[len];
        in.readFully(m.data);
        return m;
    }

    private static void send(OutputStream out, int cmd, int arg0, int arg1, byte[] data) throws IOException {
        int sum = 0;
        for (byte x : data) sum += x & 0xff;
        ByteBuffer b = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(cmd ^ 0xffffffff).put(data);
        out.write(b.array());
        out.flush();
    }

    static byte[] sign(PrivateKey key, byte[] token) throws Exception {
        Signature sig = Signature.getInstance("NONEwithRSA");
        sig.initSign(key);
        sig.update(SHA1_PREFIX);
        sig.update(token);
        return sig.sign();
    }

    // The public key the way adb sends it: base64 of {words, n0inv, modulus, rr, exponent}, little endian.
    static byte[] publicKeyBlob(PublicKey pub) throws Exception {
        RSAPublicKey k = (RSAPublicKey) pub;
        BigInteger n = k.getModulus(), e = k.getPublicExponent();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = n.mod(r32).modInverse(r32).negate().mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n); // (2^2048)^2 mod n
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + 256 + 256 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(64);
        b.putInt(n0inv.intValue());
        b.put(littleEndian(n, 256));
        b.put(littleEndian(rr, 256));
        b.putInt(e.intValue());
        return (base64(b.array()) + " tvhome@tv\0").getBytes("UTF-8");
    }

    private static byte[] littleEndian(BigInteger v, int size) {
        byte[] be = v.toByteArray(), le = new byte[size];
        for (int i = 0; i < size && i < be.length; i++) le[i] = be[be.length - 1 - i];
        return le;
    }

    private static String base64(byte[] data) {
        String abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i += 3) {
            int n = (data[i] & 0xff) << 16 | (i + 1 < data.length ? (data[i + 1] & 0xff) << 8 : 0)
                    | (i + 2 < data.length ? data[i + 2] & 0xff : 0);
            sb.append(abc.charAt(n >> 18 & 63)).append(abc.charAt(n >> 12 & 63));
            sb.append(i + 1 < data.length ? abc.charAt(n >> 6 & 63) : '=');
            sb.append(i + 2 < data.length ? abc.charAt(n & 63) : '=');
        }
        return sb.toString();
    }
}
