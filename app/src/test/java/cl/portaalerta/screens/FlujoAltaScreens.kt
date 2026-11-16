package cl.portaalerta.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import cl.portaalerta.data.Client
import cl.portaalerta.ui.FollowupDraft
import cl.portaalerta.ui.NewClientHost
import cl.portaalerta.ui.NewClientInitial
import cl.portaalerta.ui.NewClientScreen
import cl.portaalerta.ui.PortaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Capturas REALES del flujo de alta de cliente: renderizan el mismo código de la app (no una maqueta).
 * Se ejecutan con: ./gradlew -Pscreens recordRoborazziDebug  (salen en app/build/screens).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi-notnight")
class FlujoAltaScreens {
    @get:Rule
    val compose = createComposeRule()

    private class FakeHost(existing: List<Client>) : NewClientHost {
        override val countryCode = "56"
        override val months = 4
        override val defaultLeadDays = 7
        override val clients: StateFlow<List<Client>> = MutableStateFlow(existing)
        override fun save(client: Client, followup: FollowupDraft?, thenScanChip: Boolean) {}
        override fun openClient(id: Long) {}
        override fun back() {}
    }

    private fun shot(file: String, initial: NewClientInitial, existing: List<Client> = emptyList()) {
        compose.setContent { PortaTheme { NewClientScreen(FakeHost(existing), initial) } }
        compose.onRoot().captureRoboImage("build/screens/$file.png")
    }

    private val name = "Valentina Cruz"
    private val phone = "981234567"
    private val futureDate = LocalDate.of(2030, 3, 15).toEpochDay()

    @Test fun paso1Vacio() = shot("01_paso1_vacio", NewClientInitial())

    @Test fun paso1Escribiendo() = shot("02_paso1_telefono_incompleto", NewClientInitial(name = name, digits = "98123"))

    @Test fun paso1Completo() = shot("03_paso1_completo", NewClientInitial(name = name, digits = phone))

    @Test fun paso1NumeroRepetido() = shot(
        "04_paso1_numero_repetido",
        NewClientInitial(name = name, digits = phone),
        listOf(Client(id = 1, name = name, phone = "+56 9 8123 4567", currentCarrier = "Claro")),
    )

    @Test fun paso2Companias() = shot("05_paso2_companias", NewClientInitial(step = 1, name = name, digits = phone))

    @Test fun paso2Otra() = shot("06_paso2_otra", NewClientInitial(step = 1, name = name, digits = phone, otherMode = true, carrier = "Netline"))

    @Test fun paso3SinFecha() = shot("07_paso3_sin_fecha", NewClientInitial(step = 2, name = name, digits = phone, carrier = "Claro"))

    @Test fun paso3ConFecha() = shot(
        "08_paso3_con_fecha",
        NewClientInitial(step = 2, name = name, digits = phone, carrier = "Claro", pickedEpoch = futureDate),
    )

    @Test fun paso3DesdeContrato() = shot(
        "09_paso3_desde_contrato",
        NewClientInitial(step = 2, name = name, digits = phone, carrier = "Entel", fromContract = true, pickedEpoch = LocalDate.of(2029, 11, 20).toEpochDay()),
    )
}
