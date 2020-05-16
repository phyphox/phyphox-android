package de.rwth_aachen.phyphox;

import java.util.Vector;

public class FormulaParser {
    Source base = null;

    class FormulaException extends Exception {
        FormulaException() {

        }

        FormulaException(String message) {
            super(message);
        }
    }

    class Source {
        FormulaNode node = null;
        Integer index = null;
        boolean single = true;
        Double value = Double.NaN;

        Source(FormulaNode node) {
            this.node = node;
        }

        Source(Integer index, boolean single) {
            this.single = single;
            this.index = index-1;
        }

        Source(Double value) {
            this.value = value;
        }

        public Double get(Vector<Double[]> in, int i) throws FormulaException {
            if (node != null) {
                return node.calculate(in, i);
            } else if (index != null) {
                if (index >= in.size())
                    throw new FormulaException("Index too large.");
                Double[] thisIn = in.get(index);
                if (thisIn.length == 0)
                    throw new FormulaException("Empty input.");
                if (single) {
                    return thisIn[thisIn.length-1];
                } else {
                    if (i > thisIn.length)
                        throw new FormulaException("Input too short.");
                    return thisIn[i];
                }
            } else
                return value;
        }
    }

    class FormulaNode {
        Function func;
        Source in1, in2;

        FormulaNode(Function func, Source in1, Source in2) {
            this.func = func;
            this.in1 = in1;
            this.in2 = in2;
        }

        public Double calculate(Vector<Double[]> in, int i) throws FormulaException {
            return func.apply(in1.get(in, i), in2 != null ? in2.get(in, i) : null);
        }

    }

    static class Function {
        protected Double apply (Double in1, Double in2) {
            return Double.NaN;
        }
    }

    static class AddFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return in1+in2;
        }
    }

    static class MultiplyFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return in1*in2;
        }
    }

    static class SubtractFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return in1-in2;
        }
    }

    static class DivideFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return in1/in2;
        }
    }

    static class ModuloFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return in1%in2;
        }
    }

    static class PowerFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.pow(in1, in2);
        }
    }

    static class MinusFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return -in1;
        }
    }

    static class SqrtFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.sqrt(in1);
        }
    }

    static class SinFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.sin(in1);
        }
    }

    static class CosFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.cos(in1);
        }
    }

    static class TanFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.tan(in1);
        }
    }

    static class AsinFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.asin(in1);
        }
    }

    static class AcosFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.acos(in1);
        }
    }

    static class AtanFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.atan(in1);
        }
    }

    static class Atan2Function extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.atan2(in1, in2);
        }
    }

    static class SinhFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.sinh(in1);
        }
    }

    static class CoshFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.cosh(in1);
        }
    }

    static class TanhFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.tanh(in1);
        }
    }

    static class ExpFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.exp(in1);
        }
    }

    static class LogFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.log(in1);
        }
    }

    static class AbsFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.abs(in1);
        }
    }

    static class SignFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.signum(in1);
        }
    }

    static class HeavisideFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            if (in1.isNaN())
                return Double.NaN;
            return in1 >= 0 ? 1.0 : 0.0;
        }
    }

    static class RoundFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return (double)Math.round(in1);
        }
    }

    static class CeilFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.ceil(in1);
        }
    }

    static class FloorFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.floor(in1);
        }
    }

    static class MinFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.min(in1, in2);
        }
    }

    static class MaxFunction extends Function {
        protected Double apply (Double in1, Double in2) {
            return Math.max(in1, in2);
        }
    }

    private Source parse(String formula, int start, int end) throws FormulaException {

        if (start == end)
            return null;

        if (formula.charAt(start) == '(' && formula.charAt(end-1) == ')') {
            int innerBracket = 1;
            for (int j = start+1; j < end-1; j++) {
                switch (formula.charAt(j)) {
                    case '(': innerBracket++;
                              break;
                    case ')': innerBracket--;
                              break;
                }
                if (innerBracket == 0)
                    break;
            }
            if (innerBracket > 0) {
                start++;
                end--;
            }
        }

        if (formula.charAt(start) == '[' && formula.charAt(end-1) == ']') {
            boolean indexOnly = true;
            for (int j = start+1; j < end-1; j++) {
                if (formula.charAt(j) == ']') {
                    indexOnly = false;
                    break;
                }
            }
            if (indexOnly) {
                start++;
                end--;
                boolean single = formula.charAt(end - 1) != '_';
                if (!single)
                    end--;
                try {
                    Integer index = Integer.valueOf(formula.substring(start, end));
                    if (index < 1)
                        throw new FormulaException("Indices start at 1.");
                    return new Source(index, single);
                } catch (Exception e) {
                    throw new FormulaException("Could not parse index: " + formula.substring(start, end));
                }
            }
        }

        if (formula.charAt(start) == '-') {
            return new Source(new FormulaNode(new MinusFunction(), parse(formula, start+1, end), null));
        }

        int start1 = start;
        int start2 = start;
        int end1 = end;
        int end2 = end;
        Function operator = null;

        int previousPriority = 100;
        int brackets = 0;
        String cmd = "";

        //Look for "outer" operator
        for (int i = start; i < end; i++) {
            switch (formula.charAt(i)) {
                case '(':
                    brackets++;
                    break;
                case ')':
                    brackets--;
                    break;
            }

            if (brackets == 0) {
                switch (formula.charAt(i)) {
                    case '+':
                        if (previousPriority >= 1 && !cmd.equals("e")) {
                            previousPriority = 1;
                            operator = new AddFunction();
                            start1 = start;
                            end2 = end;
                            end1 = i;
                            start2 = i + 1;
                        }
                        break;
                    case '-':
                        if (previousPriority >= 1 && !cmd.equals("e") && formula.charAt(i-1) != '+' && formula.charAt(i-1) != '*' && formula.charAt(i-1) != '-' && formula.charAt(i-1) != '/' && formula.charAt(i-1) != '%' && formula.charAt(i-1) != '^') {
                            previousPriority = 1;
                            operator = new SubtractFunction();
                            start1 = start;
                            end2 = end;
                            end1 = i;
                            start2 = i + 1;
                        }
                        break;
                    case '*':
                        if (previousPriority >= 2) {
                            previousPriority = 2;
                            operator = new MultiplyFunction();
                            start1 = start;
                            end2 = end;
                            end1 = i;
                            start2 = i + 1;
                        }
                        break;
                    case '/':
                        if (previousPriority >= 2) {
                            previousPriority = 2;
                            operator = new DivideFunction();
                            start1 = start;
                            end2 = end;
                            end1 = i;
                            start2 = i + 1;
                        }
                        break;
                    case '%':
                        if (previousPriority >= 2) {
                            previousPriority = 2;
                            operator = new ModuloFunction();
                            start1 = start;
                            end2 = end;
                            end1 = i;
                            start2 = i + 1;
                        }
                        break;
                    case '^':
                        if (previousPriority >= 3) {
                            previousPriority = 3;
                            operator = new PowerFunction();
                            start1 = start;
                            end2 = end;
                            end1 = i;
                            start2 = i + 1;
                        }
                        break;
                }
            }
            if ((formula.charAt(i) >= 'a' && formula.charAt(i) <= 'z') || (!cmd.equals("") && formula.charAt(i) >= '0' && formula.charAt(i) <= '9')) {
                if (brackets == 0)
                    cmd += formula.charAt(i);
                else
                    cmd = "";
            } else {
                if (!cmd.isEmpty()) {
                    if (cmd.equals("e")) {
                        cmd = "";
                        continue;
                    }
                    if (formula.charAt(i) != '(')
                        throw new FormulaException("Function " + cmd + " needs a parameter.");

                    if (previousPriority >= 4) {

                        start1 = i+1;
                        end1 = end-1;
                        start2 = end-1;
                        end2 = end-1;

                        int innerBracket = 0;
                        for (int j = i+1; j < end; j++) {
                            if (formula.charAt(j) == ',') {
                                if (innerBracket == 0) {
                                    end1 = j;
                                    start2 = j+1;
                                    end2 = end-1;
                                }
                            } else if (formula.charAt(j) == '(') {
                                innerBracket++;
                            } else if (formula.charAt(j) == ')') {
                                innerBracket--;
                            }
                        }

                        previousPriority = 4;
                        switch (cmd) {
                            case "sqrt": operator = new SqrtFunction();
                                         break;
                            case "sin":  operator = new SinFunction();
                                         break;
                            case "cos":  operator = new CosFunction();
                                         break;
                            case "tan":  operator = new TanFunction();
                                         break;
                            case "asin": operator = new AsinFunction();
                                         break;
                            case "acos": operator = new AcosFunction();
                                         break;
                            case "atan": operator = new AtanFunction();
                                         break;
                            case "atan2": operator = new Atan2Function();
                                         break;
                            case "sinh": operator = new SinhFunction();
                                         break;
                            case "cosh": operator = new CoshFunction();
                                         break;
                            case "tanh": operator = new TanhFunction();
                                         break;
                            case "exp":  operator = new ExpFunction();
                                         break;
                            case "log":  operator = new LogFunction();
                                         break;
                            case "abs":  operator = new AbsFunction();
                                         break;
                            case "sign": operator = new SignFunction();
                                         break;
                            case "heaviside": operator = new HeavisideFunction();
                                         break;
                            case "round": operator = new RoundFunction();
                                         break;
                            case "ceil": operator = new CeilFunction();
                                         break;
                            case "floor": operator = new FloorFunction();
                                         break;
                            case "min": operator = new MinFunction();
                                         break;
                            case "max": operator = new MaxFunction();
                                         break;
                        }
                    }
                    cmd = "";
                }
            }
        }

        if (brackets != 0)
            throw new FormulaException("Brackets do not match!");

        if (operator != null)
            return new Source(new FormulaNode(operator, parse(formula, start1, end1), parse(formula, start2, end2)));
        else {
            try {
                Double v = Double.valueOf(formula.substring(start, end));
                return new Source(v);
            } catch (Exception e) {
                throw new FormulaException("No recognized operator and no parsable value: " + formula.substring(start, end));
            }
        }
    }

    FormulaParser(String formula) throws FormulaException {
        String strippedFormula = formula.replaceAll("\\s+","").toLowerCase();
        base = parse(strippedFormula, 0, strippedFormula.length());
    }

    public void execute(Vector<Double[]> in, DataOutput out) {
        int n = 0;
        for (Double[] i : in) {
            n = Math.max(n, i.length);
        }
        for (int i = 0; i < n; i++) {
            try {
                out.append(base.get(in, i));
            } catch (Exception e) {
                break;
            }
        }
    }

}
