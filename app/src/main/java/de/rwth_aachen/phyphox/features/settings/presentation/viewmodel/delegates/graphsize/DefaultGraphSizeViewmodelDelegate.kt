package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.graphsize

import de.rwth_aachen.phyphox.features.settings.domain.usecase.graphsize.GetGraphSizeRangeUseCase
import de.rwth_aachen.phyphox.features.settings.domain.usecase.graphsize.ObserveCurrentGraphSizeUseCase
import de.rwth_aachen.phyphox.features.settings.domain.usecase.graphsize.UpdateGraphSizeUseCase
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.seekbarpreferenceitem.SeekBarConfig
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.UiBuilder
import de.rwth_aachen.phyphox.utils.UiResourceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class DefaultGraphSizeViewmodelDelegate @Inject constructor(
    private val observeCurrentGraphSize: ObserveCurrentGraphSizeUseCase,
    private val getGraphSizeRange: GetGraphSizeRangeUseCase,
    private val updateGraphSize: UpdateGraphSizeUseCase,
    private val uiBuilder: UiBuilder,
) : GraphSizeViewmodelDelegate {
    private val currentGraphSizeFlow = MutableStateFlow<UiResourceState<Float>>(
        UiResourceState.Loading,
    )
    private val graphSizeRangeFlow =
        MutableStateFlow<UiResourceState<ClosedFloatingPointRange<Float>>>(
            UiResourceState.Loading,
        )

    override val graphSizeFlow: Flow<UiResourceState<SeekBarConfig>> = combine(
        currentGraphSizeFlow,
        graphSizeRangeFlow,
    ) { graphSize, range ->
        uiBuilder.buildGraphSizeUiModel(graphSize, range)
    }

    override fun start(scope: CoroutineScope) {
        observeCurrentGraphConfig(scope)
        scope.launch {
            fetchGraphSizeRange()
        }
    }

    override suspend fun updateGraphSize(size: Float) {
        TODO("Not yet implemented")
    }

    private fun observeCurrentGraphConfig(scope: CoroutineScope) {
        observeCurrentGraphSize().onStart {
            currentGraphSizeFlow.value = UiResourceState.Loading
        }.onEach {
            currentGraphSizeFlow.value = UiResourceState.Success(it)
        }.launchIn(scope)
    }

    private suspend fun fetchGraphSizeRange() {
        graphSizeRangeFlow.value = UiResourceState.Success(
            getGraphSizeRange(),
        )
    }

}
