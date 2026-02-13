package de.rwth_aachen.phyphox.features.settings.domain.usecase.graphsize

import de.rwth_aachen.phyphox.features.settings.domain.model.GraphItemsRange
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class GetGraphSizeRangesUseCase @Inject constructor(
    private val getLabelSizeMultiplierRange: GetLabelSizeMultiplierRangeDomainService,
    private val getTextSizeMultiplierRange: GetTextSizeMultiplierRangeDomainService,
    private val getLineWidthMultiplierRange: GetLineWidthMultiplierRangeDomainService,
    private val getBorderWidthMultiplierRange: GetBorderWidthMultiplierRangeDomainService,
) {
    suspend operator fun invoke(): GraphItemsRange = coroutineScope {
        val labelSizeRangeDeferred = async { getLabelSizeMultiplierRange() }
        val textSizeRangeDeferred = async { getTextSizeMultiplierRange() }
        val lineWidthRangeDeferred = async { getLineWidthMultiplierRange() }
        val borderWidthRangeDeferred = async { getBorderWidthMultiplierRange() }

        GraphItemsRange(
            labelSizeRange = labelSizeRangeDeferred.await(),
            textSizeRange = textSizeRangeDeferred.await(),
            lineWidthRange = lineWidthRangeDeferred.await(),
            borderWidthRange = borderWidthRangeDeferred.await(),
        )
    }
}
