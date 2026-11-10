package cl.portaalerta

import cl.portaalerta.data.Contact
import cl.portaalerta.data.Dates
import cl.portaalerta.data.DeviceContacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LogicTest {
    @Test
    fun waNumber_addsCountryCodeToLocalMobile() {
        assertEquals("56981234567", Contact.waNumber("9 8123 4567", "56"))
        assertEquals("56981234567", Contact.waNumber("0981234567", "56"))
    }

    @Test
    fun waNumber_keepsInternationalNumbers() {
        assertEquals("56981234567", Contact.waNumber("+56 9 8123 4567", "56"))
        assertEquals("56981234567", Contact.waNumber("56981234567", "56"))
        assertEquals("14155550100", Contact.waNumber("+1 (415) 555-0100", "56"))
    }

    @Test
    fun waNumber_landline() {
        assertEquals("56223456789", Contact.waNumber("(02) 2345 6789", "56"))
    }

    @Test
    fun isMobile_chile() {
        assertTrue(DeviceContacts.isMobile("56981234567", "56"))
        assertFalse(DeviceContacts.isMobile("56223456789", "56"))
        assertTrue(DeviceContacts.isMobile("14155550100", "1"))
    }

    @Test
    fun dueDate_fourMonthsAndMonthEnds() {
        assertEquals(LocalDate.of(2027, 3, 8), Dates.dueFor(LocalDate.of(2026, 11, 8), 4))
        assertEquals(LocalDate.of(2027, 2, 28), Dates.dueFor(LocalDate.of(2026, 10, 31), 4))
        assertEquals(LocalDate.of(2027, 2, 28), Dates.dueFor(LocalDate.of(2027, 1, 31), 1))
    }

    @Test
    fun relativeDays() {
        assertEquals("en 4 días", Dates.relative(4))
        assertEquals("mañana", Dates.relative(1))
        assertEquals("hoy", Dates.relative(0))
        assertEquals("ayer", Dates.relative(-1))
        assertEquals("hace 3 días", Dates.relative(-3))
    }
}

class IccidTest {
    private val ok19 = "8956526018159083015"
    private val ok20 = "89566613186091390997"

    @Test
    fun validIccids() {
        assertTrue(cl.portaalerta.data.Iccid.isValid(ok19))
        assertTrue(cl.portaalerta.data.Iccid.isValid(ok20))
    }

    @Test
    fun rejectsBadChecksumPrefixAndLength() {
        assertFalse(cl.portaalerta.data.Iccid.isValid("8956526018159083016"))
        assertFalse(cl.portaalerta.data.Iccid.isValid("7956526018159083015"))
        assertFalse(cl.portaalerta.data.Iccid.isValid("895652601815908301"))
    }

    @Test
    fun findsNumberInOcrText() {
        assertEquals(ok19, cl.portaalerta.data.Iccid.findIn("SIM\n8956 5260 1815 9083 015\nPUK 1234"))
        assertEquals(ok19, cl.portaalerta.data.Iccid.findIn("8956 5260 1815\n9083 015"))
        assertEquals(ok20, cl.portaalerta.data.Iccid.findIn("ICCID: 8956-6613-1860-9139-0997"))
    }

    @Test
    fun findsNumberPrintedOnTwoLinesOfTen() {
        // Así viene impreso sobre el chip: dos líneas de 10 dígitos.
        assertEquals(ok20, cl.portaalerta.data.Iccid.findIn("8956661318\n6091390997\nWOM"))
    }

    @Test
    fun fixesCommonOcrConfusions() {
        assertEquals(ok19, cl.portaalerta.data.Iccid.findIn("8956 5260 1815 9O83 O15"))
    }

    @Test
    fun readsBarcodeContent() {
        assertEquals(ok19, cl.portaalerta.data.Iccid.fromCode(ok19))
        assertEquals(null, cl.portaalerta.data.Iccid.fromCode("1234567890123"))
    }

    @Test
    fun prettyGroupsOfFour() {
        assertEquals("8956 5260 1815 9083 015", cl.portaalerta.data.Iccid.pretty(ok19))
    }
}

class PhoneTest {
    @Test
    fun normalizesWhatWasTyped() {
        assertEquals("981234567", Contact.normalizeLocal("+56 9 8123 4567", "56"))
        assertEquals("981234567", Contact.normalizeLocal("56981234567", "56"))
        assertEquals("981234567", Contact.normalizeLocal("981234567", "56"))
        assertEquals("98123", Contact.normalizeLocal("9 8123", "56"))
        assertEquals("981234567", Contact.normalizeLocal("9812345678999", "56"))
    }

    @Test
    fun completeAndPretty() {
        assertTrue(Contact.isCompleteLocal("981234567", "56"))
        assertFalse(Contact.isCompleteLocal("98123456", "56"))
        assertEquals("+56 9 8123 4567", Contact.pretty("981234567", "56"))
        // el formato guardado se vuelve a leer bien para WhatsApp
        assertEquals("56981234567", Contact.waNumber(Contact.pretty("981234567", "56"), "56"))
    }
}
