package de.rwth_aachen.phyphox.Bluetooth

import com.google.common.truth.Truth.assertThat
import de.rwth_aachen.phyphox.CorpusTestEnvironment
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.regex.PatternSyntaxException

// phyphox-test: ble-name-regex-match
//Rule ble-name-regex (phyphox-docs, spec/rules.yml): nameRegex has to match the whole device name,
// case-sensitively, name stays a substring test, both have to hold when given, and a pattern that does
// not compile refuses the file.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BluetoothNameFilterTest {

    @Test
    fun aPatternWithoutMetacharactersIsTheExactName() {
        val filter = BluetoothNameFilter(null, "phyphox:m")
        assertThat(filter.matches("phyphox:m")).isTrue()
        assertThat(filter.matches("phyphox:mini")).isFalse()
        assertThat(filter.matches("Xphyphox:m")).isFalse()
    }

    @Test
    fun prefixAndAlternationPatterns() {
        val prefix = BluetoothNameFilter(null, "phyphox:m.*")
        assertThat(prefix.matches("phyphox:m")).isTrue()
        assertThat(prefix.matches("phyphox:mini")).isTrue()
        assertThat(prefix.matches("phyphox:e")).isFalse()

        val alternatives = BluetoothNameFilter(null, "phyphox:(m|mini)")
        assertThat(alternatives.matches("phyphox:m")).isTrue()
        assertThat(alternatives.matches("phyphox:mini")).isTrue()
        assertThat(alternatives.matches("phyphox:max")).isFalse()
    }

    @Test
    fun matchingIsCaseSensitive() {
        assertThat(BluetoothNameFilter(null, "phyphox:m").matches("Phyphox:M")).isFalse()
        assertThat(BluetoothNameFilter("phyphox", null).matches("Phyphox:m")).isFalse()
    }

    @Test
    fun nameStaysASubstringTestAndBothCriteriaHaveToHold() {
        assertThat(BluetoothNameFilter("phyphox:m", null).matches("phyphox:mini")).isTrue()

        val both = BluetoothNameFilter("mini", "phyphox:(m|mini)")
        assertThat(both.matches("phyphox:mini")).isTrue()
        assertThat(both.matches("phyphox:m")).isFalse() //regex holds, name does not
        assertThat(both.matches("mini")).isFalse() //name holds, regex does not
    }

    @Test
    fun noCriterionMatchesEverything() {
        for (filter in listOf(BluetoothNameFilter(null, null), BluetoothNameFilter("", ""), BluetoothNameFilter.NONE)) {
            assertThat(filter.isEmpty).isTrue()
            assertThat(filter.matches("anything")).isTrue()
        }
        assertThat(BluetoothNameFilter(null, "x").isEmpty).isFalse()
    }

    @Test
    fun filtersWithTheSamePatternsAreOneMapKey() {
        assertThat(BluetoothNameFilter("a", "b")).isEqualTo(BluetoothNameFilter("a", "b"))
        assertThat(BluetoothNameFilter("a", "b").hashCode()).isEqualTo(BluetoothNameFilter("a", "b").hashCode())
        assertThat(BluetoothNameFilter("a", null)).isNotEqualTo(BluetoothNameFilter(null, "a"))
    }

    @Test
    fun anInvalidPatternThrowsAtConstruction() {
        assertThrows(PatternSyntaxException::class.java) { BluetoothNameFilter(null, "phyphox:(m") }
    }

    @Test
    fun theCorpusFixtureLoadsWithItsFiltersOnBothBlocks() {
        val experiment = CorpusTestEnvironment.loadGeneratedFixture("bluetooth-name-regex.phyphox")
        assertThat(experiment.bluetoothInputs).hasSize(1)
        assertThat(experiment.bluetoothInputs[0].nameFilter).isEqualTo(BluetoothNameFilter(null, "phyphox:m"))
        assertThat(experiment.bluetoothOutputs).hasSize(1)
        assertThat(experiment.bluetoothOutputs[0].nameFilter).isEqualTo(BluetoothNameFilter("phyphox", "phyphox:(m|mini)"))
    }

    @Test
    fun anInvalidPatternRefusesTheFileOnTheRealLoadingPath() {
        val message = CorpusTestEnvironment.refusalMessage("invalid/bluetooth-name-regex-invalid.phyphox")
        assertThat(message).isNotNull()
        assertThat(message).contains("nameRegex")
        assertThat(message).contains("phyphox:(m")
    }
}
