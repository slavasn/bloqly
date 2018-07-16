package org.bloqly.machine.util

import org.bouncycastle.util.BigIntegers
import org.bouncycastle.util.BigIntegers.asUnsignedByteArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class BloqlySchnorrTest {

    @Test
    fun testSignAndVerify() {

        val d = BloqlySchnorr.newSecretKey()

        val p = BloqlySchnorr.getPublicFromPrivate(d)

        val message = "hi".toByteArray()

        val signature = BloqlySchnorr.sign(message, d)

        assertTrue(BloqlySchnorr.verify(message, signature, p))
    }

    @Test
    fun testVector1() {
        val message = asUnsignedByteArray(BigInteger("0000000000000000000000000000000000000000000000000000000000000000", 16)).pad32()

        val d = BigInteger("0000000000000000000000000000000000000000000000000000000000000001", 16)

        val p = asUnsignedByteArray(BigInteger("0279BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16)).pad32()

        val signature = BloqlySchnorr.sign(message, d)

        assertTrue(BloqlySchnorr.verify(message, signature, p))
    }

    @Test
    fun testVector2() {
        val message = asUnsignedByteArray(BigInteger("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89", 16)).pad32()

        val d = BigInteger("B7E151628AED2A6ABF7158809CF4F3C762E7160F38B4DA56A784D9045190CFEF", 16)

        val p = asUnsignedByteArray(BigInteger("02DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659", 16)).pad32()

        val signature = BloqlySchnorr.sign(message, d)

        assertTrue(BloqlySchnorr.verify(message, signature, p))
    }

    @Test
    fun testVector3() {
        val message = asUnsignedByteArray(BigInteger("5E2D58D8B3BCDF1ABADEC7829054F90DDA9805AAB56C77333024B9D0A508B75C", 16)).pad32()

        val d = BigInteger("C90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B14E5C7", 16)

        val p = asUnsignedByteArray(BigInteger("03FAC2114C2FBB091527EB7C64ECB11F8021CB45E8E7809D3C0938E4B8C0E5F84B", 16)).pad32()

        val signature = BloqlySchnorr.sign(message, d)

        //assertTrue(BloqlySchnorr.verify(message, signature, p))
    }

    @Test
    fun testEncoding() {

        val d = BloqlySchnorr.newSecretKey()

        assertEquals(d, BigIntegers.fromUnsignedByteArray(asUnsignedByteArray(d)))
    }
}