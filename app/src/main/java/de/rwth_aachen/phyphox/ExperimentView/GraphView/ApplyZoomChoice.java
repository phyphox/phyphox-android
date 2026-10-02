package de.rwth_aachen.phyphox.ExperimentView.GraphView;

//The decision behind "Keep this view?" when a maximized graph is left with a zoom (InteractiveGraphView.leaveDialog):
//which axes are zoomed, what the buttons and the per-axis controls start from, and how a choice lands in the zoom state.
public final class ApplyZoomChoice {
    public enum Action { RESET, KEEP, FOLLOW }
    public enum Target { THIS, SAME_DATA, SAME_UNIT, SAME_AXIS }

    private ApplyZoomChoice() {
    }

    public static boolean isZoomed(GraphView.ZoomState z, int axis) {
        switch (axis) {
            case GraphView.AXIS_X: return !Double.isNaN(z.minX) || !Double.isNaN(z.maxX);
            case GraphView.AXIS_Y: return !Double.isNaN(z.minY) || !Double.isNaN(z.maxY);
            default: return !Double.isNaN(z.minZ) || !Double.isNaN(z.maxZ);
        }
    }

    //No question when nothing is zoomed, whatever the time axis shows
    public static boolean anyZoomed(GraphView.ZoomState z) {
        return isZoomed(z, GraphView.AXIS_X) || isZoomed(z, GraphView.AXIS_Y) || isZoomed(z, GraphView.AXIS_Z);
    }

    //The emphasised button: Keep once the user has kept a zoom before, Reset otherwise
    public static Action defaultAction(boolean previouslyKept) {
        return previouslyKept ? Action.KEEP : Action.RESET;
    }

    //What an axis control starts from: the simple choice on every zoomed axis; Keep on a following x axis is "keep and follow"
    public static Action initialAxisAction(GraphView.ZoomState z, int axis, Action simple, boolean incrementalX) {
        if (!isZoomed(z, axis) || simple == Action.RESET)
            return Action.RESET;
        if (axis == GraphView.AXIS_X && incrementalX && z.follows)
            return Action.FOLLOW;
        return Action.KEEP;
    }

    //Writes the choice into the graph: NaN = reset; a reset x axis of a followX graph goes back to following its configured window
    public static void apply(GraphView graphView, Action x, Action y, Action z) {
        GraphView.ZoomState s = graphView.zoomState;
        s.previouslyKept = x != Action.RESET || y != Action.RESET || z != Action.RESET;
        if (x == Action.RESET) {
            if (graphView.followX) {
                s.follows = true;
                s.minX = graphView.minX;
                s.maxX = graphView.maxX;
            } else {
                s.follows = false;
                s.minX = Double.NaN;
                s.maxX = Double.NaN;
            }
        } else
            s.follows = x == Action.FOLLOW;
        if (y == Action.RESET) {
            s.minY = Double.NaN;
            s.maxY = Double.NaN;
        }
        if (z == Action.RESET) {
            s.minZ = Double.NaN;
            s.maxZ = Double.NaN;
        }
        graphView.rescale();
    }
}
