package de.rwth_aachen.phyphox.ExperimentView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

// ExpView implements experiment views, which are collections of displays and graphs that form a
// specific way to show the results of an element.

// Each view consists of one or more ExpViewElements, which is a base class of an element that shows
// dataBuffer data, like a simple textDisplay showing a single value or a more complex graph.
// Hence, these elements (for example GraphElement or ValueElement) inherit from the abstract
// ExpViewElement class. ExpViewElements may even take data from the user and report it back to a
// dataBuffer to create interactive experiments (EditElement).

//Example:
//A pendulum experiment may consist of three ExpViews, showing (1) raw data, (2) an autocorrelation
//analysis and (3) the result values. The raw data ExpView would consist of three GraphElements to
//show x, y and z data. The autocorrelation would consist of a graph element showing the
//autocorrelation and a ValueElement showing the time of the first maximum. The result values
//finally only consist of two values showing the results of the analysis: A frequency and a period.



public class ExpView implements Serializable {

    public static enum State {
        hidden, normal, maximized;
    }

    //Remember? We are in the ExpView class.
    //An experiment view has a name and holds a bunch of ExpViewElement instances
    public String name;
    public Vector<ExpViewElement> elements = new Vector<>(); //The top-level elements; groups (GroupElement) hold their children themselves

    //Every element of the view in document order, groups included and followed by their children.
    //Callers that address elements individually (buffer updates, exclusive mode, the remote interface's
    //element ids) walk this; createView/destroyView run on the top level only, groups recurse themselves.
    public List<ExpViewElement> flatElements() {
        List<ExpViewElement> result = new ArrayList<>();
        collect(elements, result);
        return result;
    }

    private static void collect(Vector<ExpViewElement> elements, List<ExpViewElement> into) {
        for (ExpViewElement element : elements) {
            into.add(element);
            if (element.getChildren() != null)
                collect(element.getChildren(), into);
        }
    }
}
