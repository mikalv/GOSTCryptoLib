package com.crypto;

import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.GOST28147Engine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Base implementation of GOST 28147-89
 */
public class GOST28147SymmetricEncryption {

    private static final Logger LOG = Logger.getLogger(GOST28147SymmetricEncryption.class.getName());

    private static final int BLOCK_SIZE = 8;
    public static final String GOST_28147 = "GOST28147";
    private int[] workingKey = null;
    private byte[] key = null;

    // these are the S-boxes given in Applied Cryptography 2nd Ed., p. 333
    // This is default S-box!
    private byte S[] = {
            0x4, 0xA, 0x9, 0x2, 0xD, 0x8, 0x0, 0xE, 0x6, 0xB, 0x1, 0xC, 0x7, 0xF, 0x5, 0x3,
            0xE, 0xB, 0x4, 0xC, 0x6, 0xD, 0xF, 0xA, 0x2, 0x3, 0x8, 0x1, 0x0, 0x7, 0x5, 0x9,
            0x5, 0x8, 0x1, 0xD, 0xA, 0x3, 0x4, 0x2, 0xE, 0xF, 0xC, 0x7, 0x6, 0x0, 0x9, 0xB,
            0x7, 0xD, 0xA, 0x1, 0x0, 0x8, 0x9, 0xF, 0xE, 0x4, 0x6, 0xC, 0xB, 0x2, 0x5, 0x3,
            0x6, 0xC, 0x7, 0x1, 0x5, 0xF, 0xD, 0x8, 0x4, 0xA, 0x9, 0xE, 0x0, 0x3, 0xB, 0x2,
            0x4, 0xB, 0xA, 0x0, 0x7, 0x2, 0x1, 0xD, 0x3, 0x6, 0x8, 0x5, 0x9, 0xC, 0xF, 0xE,
            0xD, 0xB, 0x4, 0x1, 0x3, 0xF, 0x5, 0x9, 0x0, 0xA, 0xE, 0x7, 0x6, 0x8, 0x2, 0xC,
            0x1, 0xF, 0xD, 0x0, 0x5, 0x7, 0xA, 0x4, 0x9, 0x2, 0x3, 0xE, 0x6, 0xB, 0x8, 0xC
    };

    /*
     * class content S-box parameters for encrypting
     * getting from, see: http://www.ietf.org/internet-drafts/draft-popov-cryptopro-cpalgs-01.txt
     *                    http://www.ietf.org/internet-drafts/draft-popov-cryptopro-cpalgs-02.txt
     */
    private static byte[] ESbox_Test = {
            0x4, 0x2, 0xF, 0x5, 0x9, 0x1, 0x0, 0x8, 0xE, 0x3, 0xB, 0xC, 0xD, 0x7, 0xA, 0x6,
            0xC, 0x9, 0xF, 0xE, 0x8, 0x1, 0x3, 0xA, 0x2, 0x7, 0x4, 0xD, 0x6, 0x0, 0xB, 0x5,
            0xD, 0x8, 0xE, 0xC, 0x7, 0x3, 0x9, 0xA, 0x1, 0x5, 0x2, 0x4, 0x6, 0xF, 0x0, 0xB,
            0xE, 0x9, 0xB, 0x2, 0x5, 0xF, 0x7, 0x1, 0x0, 0xD, 0xC, 0x6, 0xA, 0x4, 0x3, 0x8,
            0x3, 0xE, 0x5, 0x9, 0x6, 0x8, 0x0, 0xD, 0xA, 0xB, 0x7, 0xC, 0x2, 0x1, 0xF, 0x4,
            0x8, 0xF, 0x6, 0xB, 0x1, 0x9, 0xC, 0x5, 0xD, 0x3, 0x7, 0xA, 0x0, 0xE, 0x2, 0x4,
            0x9, 0xB, 0xC, 0x0, 0x3, 0x6, 0x7, 0x5, 0x4, 0x8, 0xE, 0xF, 0x1, 0xA, 0x2, 0xD,
            0xC, 0x6, 0x5, 0x2, 0xB, 0x0, 0x9, 0xD, 0x3, 0xE, 0x7, 0xA, 0xF, 0x4, 0x1, 0x8
    };

    private static byte[] ESbox_A = {
            0x9, 0x6, 0x3, 0x2, 0x8, 0xB, 0x1, 0x7, 0xA, 0x4, 0xE, 0xF, 0xC, 0x0, 0xD, 0x5,
            0x3, 0x7, 0xE, 0x9, 0x8, 0xA, 0xF, 0x0, 0x5, 0x2, 0x6, 0xC, 0xB, 0x4, 0xD, 0x1,
            0xE, 0x4, 0x6, 0x2, 0xB, 0x3, 0xD, 0x8, 0xC, 0xF, 0x5, 0xA, 0x0, 0x7, 0x1, 0x9,
            0xE, 0x7, 0xA, 0xC, 0xD, 0x1, 0x3, 0x9, 0x0, 0x2, 0xB, 0x4, 0xF, 0x8, 0x5, 0x6,
            0xB, 0x5, 0x1, 0x9, 0x8, 0xD, 0xF, 0x0, 0xE, 0x4, 0x2, 0x3, 0xC, 0x7, 0xA, 0x6,
            0x3, 0xA, 0xD, 0xC, 0x1, 0x2, 0x0, 0xB, 0x7, 0x5, 0x9, 0x4, 0x8, 0xF, 0xE, 0x6,
            0x1, 0xD, 0x2, 0x9, 0x7, 0xA, 0x6, 0x0, 0x8, 0xC, 0x4, 0x5, 0xF, 0x3, 0xB, 0xE,
            0xB, 0xA, 0xF, 0x5, 0x0, 0xC, 0xE, 0x8, 0x6, 0x2, 0x3, 0x9, 0x1, 0x7, 0xD, 0x4
    };

    private static byte[] ESbox_B = {
            0x8, 0x4, 0xB, 0x1, 0x3, 0x5, 0x0, 0x9, 0x2, 0xE, 0xA, 0xC, 0xD, 0x6, 0x7, 0xF,
            0x0, 0x1, 0x2, 0xA, 0x4, 0xD, 0x5, 0xC, 0x9, 0x7, 0x3, 0xF, 0xB, 0x8, 0x6, 0xE,
            0xE, 0xC, 0x0, 0xA, 0x9, 0x2, 0xD, 0xB, 0x7, 0x5, 0x8, 0xF, 0x3, 0x6, 0x1, 0x4,
            0x7, 0x5, 0x0, 0xD, 0xB, 0x6, 0x1, 0x2, 0x3, 0xA, 0xC, 0xF, 0x4, 0xE, 0x9, 0x8,
            0x2, 0x7, 0xC, 0xF, 0x9, 0x5, 0xA, 0xB, 0x1, 0x4, 0x0, 0xD, 0x6, 0x8, 0xE, 0x3,
            0x8, 0x3, 0x2, 0x6, 0x4, 0xD, 0xE, 0xB, 0xC, 0x1, 0x7, 0xF, 0xA, 0x0, 0x9, 0x5,
            0x5, 0x2, 0xA, 0xB, 0x9, 0x1, 0xC, 0x3, 0x7, 0x4, 0xD, 0x0, 0x6, 0xF, 0x8, 0xE,
            0x0, 0x4, 0xB, 0xE, 0x8, 0x3, 0x7, 0x1, 0xA, 0x2, 0x9, 0x6, 0xF, 0xD, 0x5, 0xC
    };

    private static byte[] ESbox_C = {
            0x1, 0xB, 0xC, 0x2, 0x9, 0xD, 0x0, 0xF, 0x4, 0x5, 0x8, 0xE, 0xA, 0x7, 0x6, 0x3,
            0x0, 0x1, 0x7, 0xD, 0xB, 0x4, 0x5, 0x2, 0x8, 0xE, 0xF, 0xC, 0x9, 0xA, 0x6, 0x3,
            0x8, 0x2, 0x5, 0x0, 0x4, 0x9, 0xF, 0xA, 0x3, 0x7, 0xC, 0xD, 0x6, 0xE, 0x1, 0xB,
            0x3, 0x6, 0x0, 0x1, 0x5, 0xD, 0xA, 0x8, 0xB, 0x2, 0x9, 0x7, 0xE, 0xF, 0xC, 0x4,
            0x8, 0xD, 0xB, 0x0, 0x4, 0x5, 0x1, 0x2, 0x9, 0x3, 0xC, 0xE, 0x6, 0xF, 0xA, 0x7,
            0xC, 0x9, 0xB, 0x1, 0x8, 0xE, 0x2, 0x4, 0x7, 0x3, 0x6, 0x5, 0xA, 0x0, 0xF, 0xD,
            0xA, 0x9, 0x6, 0x8, 0xD, 0xE, 0x2, 0x0, 0xF, 0x3, 0x5, 0xB, 0x4, 0x1, 0xC, 0x7,
            0x7, 0x4, 0x0, 0x5, 0xA, 0x2, 0xF, 0xE, 0xC, 0x6, 0x1, 0xB, 0xD, 0x9, 0x3, 0x8
    };

    private static byte[] ESbox_D = {
            0xF, 0xC, 0x2, 0xA, 0x6, 0x4, 0x5, 0x0, 0x7, 0x9, 0xE, 0xD, 0x1, 0xB, 0x8, 0x3,
            0xB, 0x6, 0x3, 0x4, 0xC, 0xF, 0xE, 0x2, 0x7, 0xD, 0x8, 0x0, 0x5, 0xA, 0x9, 0x1,
            0x1, 0xC, 0xB, 0x0, 0xF, 0xE, 0x6, 0x5, 0xA, 0xD, 0x4, 0x8, 0x9, 0x3, 0x7, 0x2,
            0x1, 0x5, 0xE, 0xC, 0xA, 0x7, 0x0, 0xD, 0x6, 0x2, 0xB, 0x4, 0x9, 0x3, 0xF, 0x8,
            0x0, 0xC, 0x8, 0x9, 0xD, 0x2, 0xA, 0xB, 0x7, 0x3, 0x6, 0x5, 0x4, 0xE, 0xF, 0x1,
            0x8, 0x0, 0xF, 0x3, 0x2, 0x5, 0xE, 0xB, 0x1, 0xA, 0x4, 0x7, 0xC, 0x9, 0xD, 0x6,
            0x3, 0x0, 0x6, 0xF, 0x1, 0xE, 0x9, 0x2, 0xD, 0x8, 0xC, 0x4, 0xB, 0xA, 0x5, 0x7,
            0x1, 0xA, 0x6, 0x8, 0xF, 0xB, 0x0, 0x4, 0xC, 0x3, 0x5, 0x9, 0x7, 0xD, 0x2, 0xE
    };

    //S-box for digest
    private static byte DSbox_Test[] = {
            0x4, 0xA, 0x9, 0x2, 0xD, 0x8, 0x0, 0xE, 0x6, 0xB, 0x1, 0xC, 0x7, 0xF, 0x5, 0x3,
            0xE, 0xB, 0x4, 0xC, 0x6, 0xD, 0xF, 0xA, 0x2, 0x3, 0x8, 0x1, 0x0, 0x7, 0x5, 0x9,
            0x5, 0x8, 0x1, 0xD, 0xA, 0x3, 0x4, 0x2, 0xE, 0xF, 0xC, 0x7, 0x6, 0x0, 0x9, 0xB,
            0x7, 0xD, 0xA, 0x1, 0x0, 0x8, 0x9, 0xF, 0xE, 0x4, 0x6, 0xC, 0xB, 0x2, 0x5, 0x3,
            0x6, 0xC, 0x7, 0x1, 0x5, 0xF, 0xD, 0x8, 0x4, 0xA, 0x9, 0xE, 0x0, 0x3, 0xB, 0x2,
            0x4, 0xB, 0xA, 0x0, 0x7, 0x2, 0x1, 0xD, 0x3, 0x6, 0x8, 0x5, 0x9, 0xC, 0xF, 0xE,
            0xD, 0xB, 0x4, 0x1, 0x3, 0xF, 0x5, 0x9, 0x0, 0xA, 0xE, 0x7, 0x6, 0x8, 0x2, 0xC,
            0x1, 0xF, 0xD, 0x0, 0x5, 0x7, 0xA, 0x4, 0x9, 0x2, 0x3, 0xE, 0x6, 0xB, 0x8, 0xC
    };

    private static byte DSbox_A[] = {
            0xA, 0x4, 0x5, 0x6, 0x8, 0x1, 0x3, 0x7, 0xD, 0xC, 0xE, 0x0, 0x9, 0x2, 0xB, 0xF,
            0x5, 0xF, 0x4, 0x0, 0x2, 0xD, 0xB, 0x9, 0x1, 0x7, 0x6, 0x3, 0xC, 0xE, 0xA, 0x8,
            0x7, 0xF, 0xC, 0xE, 0x9, 0x4, 0x1, 0x0, 0x3, 0xB, 0x5, 0x2, 0x6, 0xA, 0x8, 0xD,
            0x4, 0xA, 0x7, 0xC, 0x0, 0xF, 0x2, 0x8, 0xE, 0x1, 0x6, 0x5, 0xD, 0xB, 0x9, 0x3,
            0x7, 0x6, 0x4, 0xB, 0x9, 0xC, 0x2, 0xA, 0x1, 0x8, 0x0, 0xE, 0xF, 0xD, 0x3, 0x5,
            0x7, 0x6, 0x2, 0x4, 0xD, 0x9, 0xF, 0x0, 0xA, 0x1, 0x5, 0xB, 0x8, 0xE, 0xC, 0x3,
            0xD, 0xE, 0x4, 0x1, 0x7, 0x0, 0x5, 0xA, 0x3, 0xC, 0x8, 0xF, 0x6, 0x2, 0x9, 0xB,
            0x1, 0x3, 0xA, 0x9, 0x5, 0xB, 0x4, 0xF, 0x8, 0x6, 0x7, 0xE, 0xD, 0x0, 0x2, 0xC
    };

    //
    // pre-defined sbox table
    //
    private static Map<String, byte[]> sBoxes = new HashMap<String, byte[]>();

    static {
        sBoxes.put("E-TEST", ESbox_Test);
        sBoxes.put("E-A", ESbox_A);
        sBoxes.put("E-B", ESbox_B);
        sBoxes.put("E-C", ESbox_C);
        sBoxes.put("E-D", ESbox_D);
        sBoxes.put("D-TEST", DSbox_Test);
        sBoxes.put("D-A", DSbox_A);
    }

    /**
     * standard constructor.
     */
    public GOST28147SymmetricEncryption() {
    }

    public void init(String sBox, byte[] key) {
        if (sBox != null && !Objects.equals(sBox, "DEFAULT")) {
            System.arraycopy(getSBox(sBox), 0, this.S, 0, getSBox(sBox).length);
        }
        this.key = key;
        workingKey = generateWorkingKey(key);
    }

    public byte[] processCipheringCBC(boolean forEncrypting, byte[] data) throws InvalidCipherTextException {
        byte[] result;
        if (forEncrypting) {
            // define data size in fist 4 bytes of data
            ByteBuffer dataSize = ByteBuffer.allocate(4);
            dataSize.putInt(data.length);
            byte[] dataWithSize = new byte[data.length + 4];
            System.arraycopy(dataSize.array(), 0, dataWithSize, 0, 4);
            System.arraycopy(data, 0, dataWithSize, 4, data.length);

            result = new byte[dataWithSize.length];
            int padding = dataWithSize.length % 8;
            if (padding != 0) {
                int extendedDataLength = dataWithSize.length + (8 - padding);
                byte[] extendedData = new byte[extendedDataLength];
                System.arraycopy(dataWithSize, 0, extendedData, 0, dataWithSize.length);
                result = encryptMessageCBC(extendedData);
            } else {
                result = decryptMessageCBC(dataWithSize);
            }
        } else {
            byte[] tmp_result = decryptMessageCBC(data);
            byte[] sizeBytes = new byte[4];
            System.arraycopy(tmp_result, 0, sizeBytes, 0, 4);
            int size = ByteBuffer.wrap(sizeBytes).getInt();
            result = new byte[size];
            System.arraycopy(tmp_result, 4, result, 0, size);
        }
        return result;

    }

    private byte[] encryptMessageCBC(byte[] src) throws InvalidCipherTextException {

        GOST28147Engine blockCipher = new GOST28147Engine();
        CBCBlockCipher cbcCipher = new CBCBlockCipher(blockCipher);
        BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(cbcCipher);

        cipher.init(true, new KeyParameter(key));

        byte[] encryptedData = new byte[cipher.getOutputSize(src.length)];
        int bytes = cipher.processBytes(src, 0, src.length, encryptedData, 0);
        cipher.doFinal(encryptedData, bytes);
        return encryptedData;
    }

    private byte[] decryptMessageCBC(byte[] src) throws InvalidCipherTextException {

        GOST28147Engine blockCipher = new GOST28147Engine();
        CBCBlockCipher cbcCipher = new CBCBlockCipher(blockCipher);
        BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(cbcCipher);

        cipher.init(false, new KeyParameter(key));

        byte[] dencryptedData = new byte[cipher.getOutputSize(src.length)];
        int bytes = cipher.processBytes(src, 0, src.length, dencryptedData, 0);
        cipher.doFinal(dencryptedData, bytes);
        return dencryptedData;
    }

    public void encryptMessage(byte[] src, byte[] out) {
        int offset = 0;
        while (offset < src.length) {
            encryptBlock(src, offset, out, offset);
            offset += 8;
        }
    }

    public void decryptMessage(byte[] src, byte[] out) {
        int offset = 0;
        while (offset < src.length) {
            decryptBlock(src, offset, out, offset);
            offset += 8;
        }
    }

    private int encryptBlock(byte[] in, int inOff, byte[] out, int outOff) {
//        in = extendIfRequired(in, inOff);
//        out = extendOutIfRequired(out, outOff);
        checkBlock(in, inOff, out, outOff);
        GOST28147Func(workingKey, in, inOff, out, outOff, true);
        return BLOCK_SIZE;
    }

    private int decryptBlock(byte[] in,
                             int inOff,
                             byte[] out,
                             int outOff) {

//        in = extendIfRequired(in, inOff);
//        out = extendIfRequired(out, outOff);
        checkBlock(in, inOff, out, outOff);
        GOST28147Func(workingKey, in, inOff, out, outOff, false);
        return BLOCK_SIZE;
    }

//    private byte[] extendIfRequired(byte[] in, int inOff) {
//        if ((inOff + BLOCK_SIZE) > in.length) {
//            byte[] newIn = new byte[inOff + BLOCK_SIZE];
//            System.arraycopy(in, 0, newIn, 0, in.length);
//            return newIn;
//        }
//        return in;
//    }
//
//    private byte[] extendOutIfRequired(byte[] in, int inOff) {
//        if ((inOff + BLOCK_SIZE) > in.length) {
//            byte[] newIn = new byte[inOff + BLOCK_SIZE];
//            System.arraycopy(in, 0, newIn, 0, in.length);
//            return newIn;
//        }
//        return in;
//    }

    private void checkBlock(byte[] in, int inOff, byte[] out, int outOff) {
        if (workingKey == null) {
            throw new IllegalStateException("GOST28147 engine not initialised");
        }

        if ((inOff + BLOCK_SIZE) > in.length) {
            throw new IllegalArgumentException("input buffer too short. Required: " + (inOff + BLOCK_SIZE) + " Actual: " + in.length);
        }

        if ((outOff + BLOCK_SIZE) > out.length) {
            throw new IllegalArgumentException("output buffer too short. Required: " + (outOff + BLOCK_SIZE) + " Actual: " + out.length);
        }


    }


    private int[] generateWorkingKey(byte[] userKey) {

        if (userKey.length != 32) {
            throw new IllegalArgumentException("Key length invalid. Key needs to be 32 byte - 256 bit!!!");
        }

        int key[] = new int[8];
        for (int i = 0; i != 8; i++) {
            key[i] = bytesToInt(userKey, i * 4);
        }

        return key;
    }

    private int GOST28147_mainStep(int n1, int key) {
        int cm = (key + n1); // CM1

        // S-box replacing

        int om = S[0 + ((cm >> (0 * 4)) & 0xF)] << (0 * 4);
        om += S[16 + ((cm >> (1 * 4)) & 0xF)] << (1 * 4);
        om += S[32 + ((cm >> (2 * 4)) & 0xF)] << (2 * 4);
        om += S[48 + ((cm >> (3 * 4)) & 0xF)] << (3 * 4);
        om += S[64 + ((cm >> (4 * 4)) & 0xF)] << (4 * 4);
        om += S[80 + ((cm >> (5 * 4)) & 0xF)] << (5 * 4);
        om += S[96 + ((cm >> (6 * 4)) & 0xF)] << (6 * 4);
        om += S[112 + ((cm >> (7 * 4)) & 0xF)] << (7 * 4);

        return om << 11 | om >>> (32 - 11); // 11-leftshift
    }

    private void GOST28147Func(
            int[] workingKey,
            byte[] in,
            int inOff,
            byte[] out,
            int outOff,
            boolean forEncrypt) {
        int N1, N2, tmp;  //tmp -> for saving N1
        N1 = bytesToInt(in, inOff);
        N2 = bytesToInt(in, inOff + 4);

        if (forEncrypt) {
            for (int k = 0; k < 3; k++)  // 1-24 steps
            {
                for (int j = 0; j < 8; j++) {
                    tmp = N1;
                    N1 = N2 ^ GOST28147_mainStep(N1, workingKey[j]); // CM2
                    N2 = tmp;
                }
            }
            for (int j = 7; j > 0; j--)  // 25-31 steps
            {
                tmp = N1;
                N1 = N2 ^ GOST28147_mainStep(N1, workingKey[j]); // CM2
                N2 = tmp;
            }
        } else //decrypt
        {
            for (int j = 0; j < 8; j++)  // 1-8 steps
            {
                tmp = N1;
                N1 = N2 ^ GOST28147_mainStep(N1, workingKey[j]); // CM2
                N2 = tmp;
            }
            for (int k = 0; k < 3; k++)  //9-31 steps
            {
                for (int j = 7; j >= 0; j--) {
                    if ((k == 2) && (j == 0)) {
                        break; // break 32 step
                    }
                    tmp = N1;
                    N1 = N2 ^ GOST28147_mainStep(N1, workingKey[j]); // CM2
                    N2 = tmp;
                }
            }
        }

        N2 = N2 ^ GOST28147_mainStep(N1, workingKey[0]);  // 32 step (N1=N1)

        intToBytes(N1, out, outOff);
        intToBytes(N2, out, outOff + 4);
    }

    //array of bytes to type int
    private int bytesToInt(
            byte[] in,
            int inOff) {
        return ((in[inOff + 3] << 24) & 0xff000000) + ((in[inOff + 2] << 16) & 0xff0000) +
                ((in[inOff + 1] << 8) & 0xff00) + (in[inOff] & 0xff);
    }

    //int to array of bytes
    private void intToBytes(
            int num,
            byte[] out,
            int outOff) {
        out[outOff + 3] = (byte) (num >>> 24);
        out[outOff + 2] = (byte) (num >>> 16);
        out[outOff + 1] = (byte) (num >>> 8);
        out[outOff] = (byte) num;
    }

    /**
     * Return the S-Box associated with SBoxName
     *
     * @param sBoxName name of the S-Box
     * @return byte array representing the S-Box
     */
    public static byte[] getSBox(
            String sBoxName) {
        byte[] namedSBox = sBoxes.get(sBoxName.toUpperCase());

        if (namedSBox != null) {
            byte[] sBox = new byte[namedSBox.length];

            System.arraycopy(namedSBox, 0, sBox, 0, sBox.length);

            return sBox;
        } else {
            throw new IllegalArgumentException("Unknown S-Box - possible types: "
                    + "\"E-Test\", \"E-A\", \"E-B\", \"E-C\", \"E-D\", \"D-Test\", \"D-A\".");
        }
    }

    /**
     * @param keyLength
     * @return
     */
    public static SecretKey generateKey(int keyLength) {
        byte[] key;
        do {
            key = new BigInteger(keyLength, new SecureRandom()).toByteArray();
        } while (key.length != keyLength / 8);
        return new SecretKeySpec(key, GOST_28147);
    }

}