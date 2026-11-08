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
