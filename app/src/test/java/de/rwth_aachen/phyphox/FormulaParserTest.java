package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.Arrays;
import java.util.Vector;

//Pins the formula language decided in the 2026-08-19 analysis-module audit: conventional
//precedence, C round semantics and load-time rejection of structurally broken formulas. The
//same cases exist as FormulaParserTests in the iOS DeserializerTests.swift - keep the two in
//step so the corpus stays a cross-platform conformance check.
public class FormulaParserTest {

    private Vector<Double[]> buffers(double[]... ins) {
        Vector<Double[]> res = new Vector<>();
        for (double[] in : ins) {
            Double[] converted = new Double[in.length];
            for (int i = 0; i < in.length; i++)
                converted[i] = in[i];
            res.add(converted);
        }
        return res;
    }

    //Mirrors FormulaParser.execute without needing a DataOutput
    private double[] eval(String formula, Vector<Double[]> in) throws FormulaParser.FormulaException {
        FormulaParser parser = new FormulaParser(formula);
        int n = 0;
        for (Double[] i : in)
            n = Math.max(n, i.length);
        double[] result = new double[n];
        int count = 0;
        for (int i = 0; i < n; i++) {
            try {
                result[count] = parser.base.get(in, i);
                count++;
            } catch (Exception e) {
                break;
            }
        }
        return Arrays.copyOf(result, count);
    }

    private double evalSingle(String formula) throws FormulaParser.FormulaException {
        double[] result = eval(formula, buffers(new double[]{0}));
        assertEquals(formula, 1, result.length);
        return result[0];
    }

    @Test
    public void conventionalPrecedence() throws Exception {
        Object[][] cases = {
                {"2^3^2", 512.},        //^ is right-associative
                {"-2^2", -4.},          //^ binds tighter than unary minus
                {"-2+3", 1.},           //unary minus applies to the following operand only
                {"-2*3", -6.},
                {"2^-2", 0.25},         //unary minus in the exponent
                {"-2^-2", -0.25},
                {"1-2-3", -4.},         //left-associative subtraction
                {"8/4/2", 1.},          //left-associative division
                {"2+3*4", 14.},
                {"2^3*2", 16.},         //^ binds tighter than *
                {"2*3^2", 18.},
                {"-(2+3)", -5.},
                {"5--3", 8.},
                {"-sin(0)+1", 1.},      //unary minus before a function call
                {"7%4", 3.},
                {"1e+5", 100000.},      //scientific notation with explicit positive exponent
                {"-1e+5", -100000.}
        };
        for (Object[] c : cases)
            assertEquals((String)c[0], (Double)c[1], evalSingle((String)c[0]), 1e-12);
    }

    @Test
    public void brokenFormulasRejectAtLoad() {
        for (String formula : new String[]{"", "5+", "+5", "*5", "-", "min(5)", "sin(1,2)", "5//3", "2^"}) {
            assertThrows("\"" + formula + "\" must be rejected at load, not produce an empty output at runtime",
                    FormulaParser.FormulaException.class, () -> new FormulaParser(formula));
        }
    }

    @Test
    public void validFormulasStillLoad() throws Exception {
        assertEquals(2., evalSingle("min(2,5)"), 0.);
        assertEquals(5., evalSingle("max(2,5)"), 0.);
        assertEquals(0., evalSingle("atan2(0,1)"), 0.);
        assertEquals(3., evalSingle("sqrt(9)"), 0.);
    }

    @Test
    public void bufferReferences() throws Exception {
        assertArrayEquals(new double[]{42}, eval("[1]+1", buffers(new double[]{41})), 0.);
        //[n_] iterates the buffer element-wise
        assertArrayEquals(new double[]{2, 4, 6}, eval("[1_]*2", buffers(new double[]{1, 2, 3})), 0.);
        //[n] reads the last value for every element
        assertArrayEquals(new double[]{6, 6, 6}, eval("[1]*2", buffers(new double[]{1, 2, 3})), 0.);
        assertArrayEquals(new double[]{-5}, eval("-[1]", buffers(new double[]{5})), 0.);
    }

    @Test
    public void roundSemantics() throws Exception {
        //C rounding: ties round half away from zero, NaN stays NaN
        assertEquals(3., evalSingle("round(2.5)"), 0.);
        assertEquals(-3., evalSingle("round(-2.5)"), 0.);
        assertEquals(Double.NaN, evalSingle("round(0/0)"), 0.);
    }
}
