package de.rwth_aachen.phyphox.camera.helper

data class DataPoint(val value: Int, var cluster: Int = UNCLASSIFIED)

const val UNCLASSIFIED = -1
const val NOISE = 0

class DBSCAN(
    private val data: List<DataPoint>,
    private val epsilon: Double,
    private val minPoints: Int
) {
    private var clusterIndex = 0

    fun run() {
        for (point in data) {
            if (point.cluster == UNCLASSIFIED) {
                if (expandCluster(point)) {
                    clusterIndex++
                }
            }
        }
    }

    private fun expandCluster(point: DataPoint): Boolean {
        val neighbors = regionQuery(point)
        if (neighbors.size < minPoints) {
            point.cluster = NOISE
            return false
        }

        assignCluster(point, neighbors)

        var i = 0
        while (i < neighbors.size) {
            val neighbor = neighbors[i]
            if (neighbor.cluster == UNCLASSIFIED || neighbor.cluster == NOISE) {
                if (neighbor.cluster == UNCLASSIFIED) {
                    val neighborNeighbors = regionQuery(neighbor)
                    if (neighborNeighbors.size >= minPoints) {
                        neighbors.addAll(neighborNeighbors)
                    }
                }
                assignCluster(neighbor, neighbors)
            }
            i++
        }
        return true
    }

    private fun regionQuery(point: DataPoint): MutableList<DataPoint> {
        return data.filter { Math.abs(it.value - point.value) <= epsilon }.toMutableList()
    }

    private fun assignCluster(point: DataPoint, neighbors: MutableList<DataPoint>) {
        point.cluster = clusterIndex
        for (neighbor in neighbors) {
            neighbor.cluster = clusterIndex
        }
    }
}

