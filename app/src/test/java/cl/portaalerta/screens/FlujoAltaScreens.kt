package cl.portaalerta.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import cl.portaalerta.NewClientDraft
import cl.portaalerta.data.Client
import cl.portaalerta.ui.ChipDraft
import cl.portaalerta.ui.FollowupDraft
import cl.portaalerta.ui.NewClientHost
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
@Config(sdk = [34], qualifiers = "w390dp-h844dp-notnight-xxhdpi")
class FlujoAltaScreens {
    @get:Rule
    val compose = createComposeRule()

    private class FakeHost(existing: List<Client>, override val draft: NewClientDraft) : NewClientHost {
        override val countryCode = "56"
        override val months = 4
        override val clients: StateFlow<List<Client>> = MutableStateFlow(existing)
        override fun save(client: Client, followup: FollowupDraft?, chip: ChipDraft?) {}
        override fun scanChip() {}
        override fun openClient(id: Long) {}
        override fun exit() {}
    }

    private fun shot(file: String, draft: NewClientDraft, existing: List<Client> = emptyList()) {
        compose.setContent { PortaTheme { NewClientScreen(FakeHost(existing, draft)) } }
        compose.onRoot().captureRoboImage("build/screens/$file.png")
    }

    private val name = "Valentina Cruz"
    private val phone = "981234567"
    private val future = LocalDate.of(2030, 3, 15).toEpochDay()
    private val chip = "8956526018159083015"

    @Test fun paso1Vacio() = shot("01_paso1_vacio", NewClientDraft())

    @Test fun paso1Escribiendo() = shot("02_paso1_telefono_incompleto", NewClientDraft(name = name, digits = "98123"))

    @Test fun paso1Completo() = shot("03_paso1_completo", NewClientDraft(name = name, digits = phone))

    @Test fun paso1NumeroRepetido() = shot(
        "04_paso1_numero_repetido",
        NewClientDraft(name = name, digits = phone),
        listOf(Client(id = 1, name = name, phone = "+56 9 8123 4567", currentCarrier = "Claro")),
    )

    @Test fun paso2Companias() = shot("05_paso2_companias", NewClientDraft(step = 1, name = name, digits = phone))

    @Test fun paso2Otra() = shot("06_paso2_otra", NewClientDraft(step = 1, name = name, digits = phone, otherMode = true, carrier = "Netline"))

    @Test fun paso3SinFecha() = shot("07_paso3_sin_fecha", NewClientDraft(step = 2, name = name, digits = phone, carrier = "Claro"))

    @Test fun paso3ConFecha() = shot(
        "08_paso3_con_fecha",
        NewClientDraft(step = 2, name = name, digits = phone, carrier = "Claro", pickedEpoch = future),
    )

    @Test fun paso3DesdeContrato() = shot(
        "09_paso3_desde_contrato",
        NewClientDraft(step = 2, name = name, digits = phone, carrier = "Entel", fromContract = true, pickedEpoch = LocalDate.of(2029, 11, 20).toEpochDay()),
    )

    @Test fun paso4SinChip() = shot(
        "10_paso4_sin_chip",
        NewClientDraft(step = 3, name = name, digits = phone, carrier = "Claro", pickedEpoch = future),
    )

    @Test fun paso4ConChip() = shot(
        "11_paso4_con_chip_leido",
        NewClientDraft(step = 3, name = name, digits = phone, carrier = "Claro", pickedEpoch = future, chipIccid = chip),
    )

    @Test fun paso4SinFecha() = shot(
        "12_paso4_sin_fecha",
        NewClientDraft(step = 3, name = name, digits = phone, carrier = "Claro"),
    )
}
