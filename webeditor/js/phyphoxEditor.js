function phyphoxXMLBuilder() {

    var version = "1.0";

// Helper

    var indent = function(n) {
        indentation = "";
        for (i = 0; i < n; i++)
            indentation += "    ";
        return indentation;
    }

    var htmlEntities = function(str) {
        return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    var nl2br = function (str) {
        return String(str).replace(/\r/g, '').replace(/\n/g, '<br />');
    }

    var space2nbsp = function (str) {
        return String(str).replace(/\ /g, '&nbsp;');
    }

// Data-Types and properties

    var xmlProperty = function(name) {
        this.value; //Current value
        this.pname = name; //Property name
        this.help;  //Help shown to user to explain, what this does...

        this.setValue = function(x) {
            this.value = x;
        }
    }

    xmlProperty.prototype.generateXML = function() {
    }

    var xmlPropertyBoolean = function(name, init) {
        xmlProperty.call(this, name);
        this.setValue(init);
    }
    xmlPropertyBoolean.prototype = new xmlProperty();
    xmlPropertyBoolean.prototype.constructor = xmlPropertyBoolean;

    xmlPropertyBoolean.prototype.setValue = function(v) {
        this.value = (v == "true");
    }

    xmlPropertyBoolean.prototype.generateXML = function() {
        if (this.value)
            return this.pname + "=\"true\"";
        else
            return this.pname + "=\"false\"";
    }

    var xmlPropertyInt = function(name, init) {
        xmlProperty.call(this, name);
        this.setValue(init);
    }
    xmlPropertyInt.prototype = new xmlProperty();
    xmlPropertyInt.prototype.constructor = xmlPropertyInt;

    xmlPropertyInt.prototype.setValue = function(v) {
        this.value = parseInt(v);
    }

    xmlPropertyInt.prototype.generateXML = function() {
        return this.pname + "=\"" + this.value + "\"";
    }

    var xmlPropertyDouble = function(name, init) {
        xmlProperty.call(this, name);
        this.setValue(init);
    }
    xmlPropertyDouble.prototype = new xmlProperty();
    xmlPropertyDouble.prototype.constructor = xmlPropertyDouble;

    xmlPropertyDouble.prototype.setValue = function(v) {
        this.value = parseFloat(v);
    }

    xmlPropertyDouble.prototype.generateXML = function() {
        return this.pname + "=\"" + this.value + "\"";
    }

    var xmlPropertyString = function(name, init) {
        xmlProperty.call(this, name);
        this.setValue(init);
    }
    xmlPropertyString.prototype = new xmlProperty();
    xmlPropertyString.prototype.constructor = xmlPropertyString;

    xmlPropertyString.prototype.setValue = function(v) {
        this.value = v;
    }

    xmlPropertyString.prototype.generateXML = function() {
        return this.pname + "=\"" + htmlEntities(this.value) + "\"";
    }

    var xmlPropertyList = function(name, init, list) {
        xmlProperty.call(this, name);
        this.list = list;
        this.setValue(init);
    }
    xmlPropertyList.prototype = new xmlProperty();
    xmlPropertyList.prototype.constructor = xmlPropertyList;

    xmlPropertyList.prototype.setValue = function(v) {
        for (var key in this.list) {
            if (key == v)
                this.value = v;
        }
    }

    xmlPropertyList.prototype.generateXML = function() {
        return this.pname + "=\"" + this.value + "\"";
    }

// Buffers

    this.bufferList = function() {
        var buffer = function(name, size, isStatic) {
            this.bname = name;
            this.size = size;
            this.isStatic = isStatic;


            this.getName = function() {
                return this.bname;
            }

            this.getSize = function() {
                return this.size;
            }

            this.getStatic = function() {
                return this.size;
            }
        }

        this.list = new Array();

        this.addBuffer = function(name, size, isStatic) {
            isStatic = (typeof isStatic === 'undefined') ? false : isStatic;
            b = new buffer(name, size, isStatic);
            this.list.push(b);
            return b;
        }

        this.removeBuffer = function(buffer) {
            for (var i = 0; i < this.list.length; i++) {
                if (this.list[i] == buffer) {
                    this.list.splice(i, 1);
                    break;
                }
            }
        }

        this.makeNamesUnique = function() {
            //TODO: Should add indices to buffer names, that already exist before creating the xml
        }

        this.generateXML = function () {
            var code = indent(1) + "<data-containers>\n";
            for (var i = 0; i < this.list.length; i++)
                code += indent(2) + "<container size=\"" + this.list[i].getSize() + "\" static=\"" + (this.list[i].getStatic() ? "true" : "false") + "\">" + this.list[i].getName() + "</container>\n";
            code += indent(1) + "</data-containers>\n";
            return code;
        }

    }

// Input-Modules

    this.inputModule = function() {
        var type;
        var rate;
        var properties;
        var output;
        var outputKey;
    }

    this.inputModule.prototype.generateXML = function () {
        var code = indent(2) + "<" + this.type;
        code += " " + this.rate.generateXML();
        for (var i = 0; i < this.properties.length; i++) {
            code += " " + this.properties[i].generateXML();
        }
        code += ">\n";
        for (i = 0; i < this.output.length; i++) {
            if (this.output[i]["output"] === null)
                continue;
            code += indent(3);
            if (typeof this.outputKey === "undefined")
                code += "<output>";
            else
                code += "<output " + this.outputKey + "=\"" + this.output[i]["key"] + "\">";
            code += htmlEntities(this.output[i]["output"].getName());
            code += "</output>\n";
        }
        code += indent(2) + "</" + this.type + ">\n";
        return code;
    }

// Input-Modules: Audio

    this.inputModuleAudio = function() {
        this.type = "audio";
        this.rate = new xmlPropertyDouble("rate", 48000);
        this.properties = new Array();
        this.output = new Array();
        this.output[0] = {key: "output", output: buffers.addBuffer("recording", 48000)};
    }
    this.inputModuleAudio.prototype = new this.inputModule();
    this.inputModuleAudio.prototype.constructor = this.inputModuleAudio;

// Input-Modules: Sensor

    this.inputModuleSensor = function() {
        this.type = "sensor";
        this.rate = new xmlPropertyDouble("rate", 0);
        this.properties = new Array();
        this.properties[0] = new xmlPropertyBoolean("average", false);
        this.properties[1] = new xmlPropertyList("type", "accelerometer", {"accelerometer": "Accelerometer", "linear_acceleration": "Linear Acceleration", "light": "Light", "gyroscope": "Gyroscope", "magnetic_field": "Magnetic Field", "pressure": "Pressure"});
        this.output = new Array();
        this.output[0] = {key: "x", output: buffers.addBuffer("sensorX", 1000)};
        this.output[1] = {key: "y", output: buffers.addBuffer("sensorY", 1000)};
        this.output[2] = {key: "z", output: buffers.addBuffer("sensorZ", 1000)};
        this.output[3] = {key: "t", output: buffers.addBuffer("sensorT", 1000)};
        this.outputKey = "component";
    }
    this.inputModuleSensor.prototype = new this.inputModule();
    this.inputModuleSensor.prototype.constructor = this.inputModuleSensor;

// Output-Modules

    this.outputModule = function() {
        var type;
        var properties;
        var input;
        var inputKey;
    }

    this.outputModule.prototype.generateXML = function () {
        var code = indent(2) + "<" + this.type;
        for (var i = 0; i < this.properties.length; i++) {
            code += " " + this.properties[i].generateXML();
        }
        code += ">\n";
        for (i = 0; i < this.input.length; i++) {
            if (this.input[i]["input"] === null)
                continue;
            code += indent(3);
            if (typeof this.inputKey === "undefined")
                code += "<input>";
            else
                code += "<input " + this.inputKey + "=\"" + this.input[i]["key"] + "\">";
            code += htmlEntities(this.input[i]["input"].getName());
            code += "</input>\n";
        }
        code += indent(2) + "</" + this.type + ">\n";
        return code;
    }

    this.outputModule.prototype.setInput = function(list) {
        for (i = 0; i < list.length; i++)
            this.input[i]["input"] = list[i];
    }

// Output-Modules: Audio

    this.outputModuleAudio = function() {
        this.type = "audio";
        this.properties = new Array();
        this.properties[0] = new xmlPropertyBoolean("loop", false);
        this.properties[1] = new xmlPropertyInt("rate", 48000);
        this.input = new Array();
        this.input[0] = {key: "input", multiple: false, input: null};
    }
    this.outputModuleAudio.prototype = new this.outputModule();
    this.outputModuleAudio.prototype.constructor = this.outputModuleAudio;

// Analysis-Modules

    this.analysisModule = function() {
        var type;
        var properties;
        var input;
        var output;
    }

    this.analysisModule.prototype.generateXML = function () {
        var code = indent(2) + "<" + this.type;
        for (var i = 0; i < this.properties.length; i++) {
            code += " " + this.properties[i].generateXML();
        }
        code += ">\n";
        for (i = 0; i < this.input.length; i++) {
            if (this.input[i]["input"] === null)
                continue;
            code += indent(3);
            if (typeof this.input[i]["input"] === "object") {
                code += "<input as=\"" + this.input[i]["key"] + "\">";
                code += htmlEntities(this.input[i]["input"].getName());
            } else {
                code += "<input as=\"" + this.input[i]["key"] + "\" type=\"value\">";
                code += this.input[i]["input"];
            }
            code += "</input>\n";
        }
        for (i = 0; i < this.output.length; i++) {
            if (this.output[i]["output"] === null)
                continue;
            code += indent(3);
            code += "<output as=\"" + this.output[i]["key"] + "\">";
            code += htmlEntities(this.output[i]["output"].getName());
            code += "</output>\n";
        }
        code += indent(2) + "</" + this.type + ">\n";
        return code;
    }

    this.analysisModule.prototype.setInput = function(list) {
        for (i = 0; i < list.length; i++)
            this.input[i]["input"] = list[i];
    }

// Analysis-Modules: Add

    this.analysisModuleAdd = function() {
        this.type = "add";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "summand", multiple: true, input: null};
        this.output = new Array();
        this.output[0] = {key: "sum", multiple: true, output: null};
    }
    this.analysisModuleAdd.prototype = new this.analysisModule();
    this.analysisModuleAdd.prototype.constructor = this.analysisModuleAdd;

// Analysis-Modules: Subtract

    this.analysisModuleSubtract = function() {
        this.type = "subtract";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "minuend", multiple: false, input: null};
        this.input[1] = {key: "subtrahend", multiple: true, input: null};
        this.output = new Array();
        this.output[0] = {key: "difference", multiple: true, output: null};
    }
    this.analysisModuleSubtract.prototype = new this.analysisModule();
    this.analysisModuleSubtract.prototype.constructor = this.analysisModuleSubtract;

// Analysis-Modules: Multiply

    this.analysisModuleMultiply = function() {
        this.type = "multiply";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "factor", multiple: true, input: null};
        this.output = new Array();
        this.output[0] = {key: "product", multiple: true, output: null};
    }
    this.analysisModuleMultiply.prototype = new this.analysisModule();
    this.analysisModuleMultiply.prototype.constructor = this.analysisModuleMultiply;

// Analysis-Modules: Divide

    this.analysisModuleDivide = function() {
        this.type = "divide";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "dividend", multiple: false, input: null};
        this.input[1] = {key: "divisor", multiple: true, input: null};
        this.output = new Array();
        this.output[0] = {key: "quotient", multiple: true, output: null};
    }
    this.analysisModuleDivide.prototype = new this.analysisModule();
    this.analysisModuleDivide.prototype.constructor = this.analysisModuleDivide;

// Analysis-Modules: Power

    this.analysisModulePower = function() {
        this.type = "power";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "base", multiple: false, input: null};
        this.input[1] = {key: "exponent", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "power", multiple: true, output: null};
    }
    this.analysisModulePower.prototype = new this.analysisModule();
    this.analysisModulePower.prototype.constructor = this.analysisModulePower;

// Analysis-Modules: GCD

    this.analysisModuleGCD = function() {
        this.type = "gcd";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "value", multiple: false, input: null};
        this.input[1] = {key: "value", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "gcd", multiple: true, output: null};
    }
    this.analysisModuleGCD.prototype = new this.analysisModule();
    this.analysisModuleGCD.prototype.constructor = this.analysisModuleGCD;

// Analysis-Modules: LCM

    this.analysisModuleLCM = function() {
        this.type = "lcm";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "value", multiple: false, input: null};
        this.input[1] = {key: "value", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "lcm", multiple: true, output: null};
    }
    this.analysisModuleLCM.prototype = new this.analysisModule();
    this.analysisModuleLCM.prototype.constructor = this.analysisModuleLCM;

// Analysis-Modules: abs

    this.analysisModuleAbs = function() {
        this.type = "abs";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "value", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "abs", multiple: true, output: null};
    }
    this.analysisModuleAbs.prototype = new this.analysisModule();
    this.analysisModuleAbs.prototype.constructor = this.analysisModuleAbs;

// Analysis-Modules: sin

    this.analysisModuleSin = function() {
        this.type = "sin";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "value", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "sin", multiple: true, output: null};
    }
    this.analysisModuleSin.prototype = new this.analysisModule();
    this.analysisModuleSin.prototype.constructor = this.analysisModuleSin;

// Analysis-Modules: cos

    this.analysisModuleCos = function() {
        this.type = "cos";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "value", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "cos", multiple: true, output: null};
    }
    this.analysisModuleCos.prototype = new this.analysisModule();
    this.analysisModuleCos.prototype.constructor = this.analysisModuleCos;

// Analysis-Modules: tan

    this.analysisModuleTan = function() {
        this.type = "tan";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "value", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "tan", multiple: true, output: null};
    }
    this.analysisModuleTan.prototype = new this.analysisModule();
    this.analysisModuleTan.prototype.constructor = this.analysisModuleTan;

// Analysis-Modules: first

    this.analysisModuleFirst = function() {
        this.type = "first";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "value", multiple: true, input: null};
        this.output = new Array();
        this.output[0] = {key: "first", multiple: true, output: null};
    }
    this.analysisModuleFirst.prototype = new this.analysisModule();
    this.analysisModuleFirst.prototype.constructor = this.analysisModuleFirst;

// Analysis-Modules: max

    this.analysisModuleMax = function() {
        this.type = "max";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "x", multiple: false, input: null};
        this.input[1] = {key: "y", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "max", multiple: false, output: null};
        this.output[1] = {key: "position", multiple: false, output: null};
    }
    this.analysisModuleMax.prototype = new this.analysisModule();
    this.analysisModuleMax.prototype.constructor = this.analysisModuleMax;

// Analysis-Modules: threshold

    this.analysisModuleThreshold = function() {
        this.type = "threshold";
        this.properties = new Array();
        this.properties[0] = new xmlPropertyBoolean("falling", false);
        this.input = new Array();
        this.input[0] = {key: "x", multiple: false, input: null};
        this.input[1] = {key: "y", multiple: false, input: null};
        this.output[2] = {key: "threshold", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "position", multiple: false, output: null};
    }
    this.analysisModuleThreshold.prototype = new this.analysisModule();
    this.analysisModuleThreshold.prototype.constructor = this.analysisModuleThreshold;

// Analysis-Modules: append

    this.analysisModuleAppend = function() {
        this.type = "append";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "in", multiple: true, input: null};
        this.output = new Array();
        this.output[0] = {key: "out", multiple: false, output: null};
    }
    this.analysisModuleAppend.prototype = new this.analysisModule();
    this.analysisModuleAppend.prototype.constructor = this.analysisModuleAppend;

// Analysis-Modules: fft

    this.analysisModuleFFT = function() {
        this.type = "fft";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "re", multiple: false, input: null};
        this.input[1] = {key: "im", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "re", multiple: false, output: null};
        this.output[1] = {key: "im", multiple: false, output: null};
    }
    this.analysisModuleFFT.prototype = new this.analysisModule();
    this.analysisModuleFFT.prototype.constructor = this.analysisModuleFFT;

// Analysis-Modules: autocorrelation

    this.analysisModuleAutocorrelation = function() {
        this.type = "autocorrelation";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "x", multiple: false, input: null};
        this.input[1] = {key: "y", multiple: false, input: null};
        this.input[2] = {key: "minX", multiple: false, input: null};
        this.input[3] = {key: "maxX", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "x", multiple: false, output: null};
        this.output[1] = {key: "y", multiple: false, output: null};
    }
    this.analysisModuleAutocorrelation.prototype = new this.analysisModule();
    this.analysisModuleAutocorrelation.prototype.constructor = this.analysisModuleAutocorrelation;

// Analysis-Modules: differentiate

    this.analysisModuleDifferentiate = function() {
        this.type = "differentiate";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "in", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "out", multiple: false, output: null};
    }
    this.analysisModuleDifferentiate.prototype = new this.analysisModule();
    this.analysisModuleDifferentiate.prototype.constructor = this.analysisModuleDifferentiate;

// Analysis-Modules: integrate

    this.analysisModuleIntegrate = function() {
        this.type = "integrate";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "in", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "out", multiple: false, output: null};
    }
    this.analysisModuleIntegrate.prototype = new this.analysisModule();
    this.analysisModuleIntegrate.prototype.constructor = this.analysisModuleIntegrate;

// Analysis-Modules: crosscorrelation

    this.analysisModuleCrosscorrelation = function() {
        this.type = "crosscorrelation";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "in", multiple: false, input: null};
        this.input[1] = {key: "in", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "out", multiple: false, output: null};
    }
    this.analysisModuleCrosscorrelation.prototype = new this.analysisModule();
    this.analysisModuleCrosscorrelation.prototype.constructor = this.analysisModuleCrosscorrelation;

// Analysis-Modules: gausssmooth

    this.analysisModuleGausssmooth = function() {
        this.type = "gausssmooth";
        this.properties = new Array();
        this.properties[0] = new xmlPropertyDouble("sigma", 3.0);
        this.input = new Array();
        this.input[0] = {key: "in", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "out", multiple: false, output: null};
    }
    this.analysisModuleGausssmooth.prototype = new this.analysisModule();
    this.analysisModuleGausssmooth.prototype.constructor = this.analysisModuleGausssmooth;

// Analysis-Modules: rangefilter

    this.analysisModuleRangefilter = function() {
        this.type = "rangefilter";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "input", multiple: true, input: null};
        this.input[1] = {key: "min", multiple: true, input: null};
        this.input[2] = {key: "max", multiple: true, input: null};
        this.output = new Array();
        this.output[0] = {key: "out", multiple: true, output: null};
    }
    this.analysisModuleRangefilter.prototype = new this.analysisModule();
    this.analysisModuleRangefilter.prototype.constructor = this.analysisModuleRangefilter;

// Analysis-Modules: ramp

    this.analysisModuleRamp = function() {
        this.type = "ramp";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "start", multiple: false, input: null};
        this.input[1] = {key: "stop", multiple: false, input: null};
        this.input[2] = {key: "length", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "out", multiple: false, output: null};
    }
    this.analysisModuleRamp.prototype = new this.analysisModule();
    this.analysisModuleRamp.prototype.constructor = this.analysisModuleRamp;

// Analysis-Modules: const

    this.analysisModuleConst = function() {
        this.type = "const";
        this.properties = new Array();
        this.input = new Array();
        this.input[0] = {key: "value", multiple: false, input: null};
        this.input[1] = {key: "length", multiple: false, input: null};
        this.output = new Array();
        this.output[0] = {key: "out", multiple: false, output: null};
    }
    this.analysisModuleConst.prototype = new this.analysisModule();
    this.analysisModuleConst.prototype.constructor = this.analysisModuleConst;


///////////////////////////////////////////////////////////////////////////////////////////////

// View-Elements

    this.viewElement = function() {
        var type;
        var properties;
        var input;
        var inputKey;
        var input;
        var outputKey;
    }

    this.viewElement.prototype.generateXML = function () {
        var code = indent(3) + "<" + this.type;
        for (var i = 0; i < this.properties.length; i++) {
            code += " " + this.properties[i].generateXML();
        }
        code += ">\n";
        for (i = 0; i < this.input.length; i++) {
            if (this.input[i]["input"] === null)
                continue;
            code += indent(4);
            if (typeof this.inputKey === "undefined")
                code += "<input>";
            else
                code += "<input " + this.inputKey + "=\"" + this.input[i]["key"] + "\">";
            code += htmlEntities(this.input[i]["input"].getName());
            code += "</input>\n";
        }
        for (i = 0; i < this.output.length; i++) {
            if (this.output[i]["output"] === null)
                continue;
            code += indent(4);
            if (typeof this.outputKey === "undefined")
                code += "<output>";
            else
                code += "<output " + this.outputKey + "=\"" + this.output[i]["key"] + "\">";
            code += htmlEntities(this.input[i]["output"].getName());
            code += "</output>\n";
        }
        code += indent(3) + "</" + this.type + ">\n";
        return code;
    }

    this.viewElement.prototype.setInput = function(list) {
        for (i = 0; i < list.length; i++)
            this.input[i]["input"] = list[i];
    }

// View-Element: info

    this.viewElementInfo = function() {
        this.type = "info";
        this.properties = new Array();
        this.properties[0] = new xmlPropertyString("label", "");
        this.properties[1] = new xmlPropertyDouble("labelSize", 1.0);
        this.input = new Array();
        this.output = new Array();
    }
    this.viewElementInfo.prototype = new this.viewElement();
    this.viewElementInfo.prototype.constructor = this.viewElementInfo;

// View-Element: value

    this.viewElementValue = function() {
        this.type = "value";
        this.properties = new Array();
        this.properties[0] = new xmlPropertyString("label", "");
        this.properties[1] = new xmlPropertyDouble("labelSize", 1.0);
        this.properties[2] = new xmlPropertyInt("precision", 2);
        this.properties[3] = new xmlPropertyBoolean("scientific", false);
        this.properties[4] = new xmlPropertyString("unit", "");
        this.properties[5] = new xmlPropertyDouble("factor", 1.0);
        this.input = new Array();
        this.input[0] = {key: "input", input: null};
        this.output = new Array();
    }
    this.viewElementValue.prototype = new this.viewElement();
    this.viewElementValue.prototype.constructor = this.viewElementValue;

// View-Element: graph

    this.viewElementGraph = function() {
        this.type = "graph";
        this.properties = new Array();
        this.properties[0] = new xmlPropertyString("label", "");
        this.properties[1] = new xmlPropertyDouble("labelSize", 1.0);
        this.properties[2] = new xmlPropertyDouble("aspectRatio", 3.0);
        this.properties[3] = new xmlPropertyList("style", "lines", {"lines": "Lines", "dots": "Dots"});
        this.properties[4] = new xmlPropertyBoolean("partialUpdate", false);
        this.properties[5] = new xmlPropertyBoolean("forceFullDataset", false);
        this.properties[6] = new xmlPropertyInt("history", 1);
        this.properties[7] = new xmlPropertyString("labelX", "");
        this.properties[8] = new xmlPropertyString("labelY", "");
        this.properties[9] = new xmlPropertyBoolean("logX", false);
        this.properties[10] = new xmlPropertyBoolean("logY", false);
        this.input = new Array();
        this.input[0] = {key: "x", input: null};
        this.input[1] = {key: "y", input: null};
        this.inputKey = "axis";
        this.output = new Array();
    }
    this.viewElementGraph.prototype = new this.viewElement();
    this.viewElementGraph.prototype.constructor = this.viewElementGraph;

// View-Element: edit

    this.viewElementEdit = function() {
        this.type = "edit";
        this.properties = new Array();
        this.properties[0] = new xmlPropertyString("label", "");
        this.properties[1] = new xmlPropertyDouble("labelSize", 1.0);
        this.properties[2] = new xmlPropertyBoolean("signed", true);
        this.properties[3] = new xmlPropertyBoolean("decimal", true);
        this.properties[4] = new xmlPropertyString("unit", "");
        this.properties[5] = new xmlPropertyDouble("factor", 1.0);
        this.properties[6] = new xmlPropertyDouble("default", 0.0);
        this.input = new Array();
        this.output = new Array();
        this.output[0] = {key: "output", output: null};
    }
    this.viewElementEdit.prototype = new this.viewElement();
    this.viewElementEdit.prototype.constructor = this.viewElementEdit;

// View group

    this.viewGroup = function(label) {
        this.label = label;
        this.elements = new Array();
    }

    this.viewGroup.prototype.generateXML = function () {
        var code = indent(2) + "<view name=\"" + this.label + "\">\n";
        for (i = 0; i < this.elements.length; i++) {
            code += this.elements[i].generateXML();
        }
        code += indent(2) + "</view>\n";
        return code;
    }

    this.viewGroup.prototype.addElement = function (element) {
        this.elements.push(element);
    }


///////////////////////////////////////////////////////////////////////////////////////////////

    //phyphox editor variables

    var title = "";
    var category = "";
    var description = "";
    var buffers = new this.bufferList();
    var inputModules = new Array();
    var outputModules = new Array();
    var analysisModules = new Array();
    var viewGroups = new Array();

    //Basic interfaces
    this.setTitle = function (x) {
        title = x;
    }

    this.getTitle = function () {
        return title;
    }

    this.setCategory = function (x) {
        category = x;
    }

    this.getCategory = function () {
        return category;
    }

    this.setDescription = function (x) {
        description = x;
    }

    this.getDescription = function () {
        return description;
    }

    //Interface to create and handle instances...

    this.addInputModule = function(module) {
        inputModules.push(module);
    }

    this.addOutputModule = function(module) {
        outputModules.push(module);
    }

    this.addAnalysisModule = function(module) {
        analysisModules.push(module);
    }

    this.addViewGroup = function(viewGroup) {
        viewGroups.push(viewGroup);
    }

    this.generateXML = function(htmlPreview) {
        xml = "<phyphox version=\"" + version + "\">" + "\n";

        xml += indent(1) + "<title>" + htmlEntities(title) + "</title>\n";
        xml += indent(1) + "<category>" + htmlEntities(category) + "</category>\n";
        xml += indent(1) + "<description>" + htmlEntities(description) + "</description>\n";

        xml += buffers.generateXML();

        xml += indent(1) + "<input>\n";
        for (var i = 0; i < inputModules.length; i++) {
            xml += inputModules[i].generateXML();
        }
        xml += indent(1) + "</input>\n";

        xml += indent(1) + "<output>\n";
        for (var i = 0; i < outputModules.length; i++) {
            xml += outputModules[i].generateXML();
        }
        xml += indent(1) + "</output>\n";

        xml += indent(1) + "<analysis>\n";
        for (var i = 0; i < analysisModules.length; i++) {
            xml += analysisModules[i].generateXML();
        }
        xml += indent(1) + "</analysis>\n";

        xml += indent(1) + "<views>\n";
        for (var i = 0; i < viewGroups.length; i++) {
            xml += viewGroups[i].generateXML();
        }
        xml += indent(1) + "</views>\n";

        xml += "</phyphox>\n";

        if (htmlPreview)
            return nl2br(space2nbsp(htmlEntities(xml)));
        return xml;
    }
}

function phyphoxEditor(rootID) {

    var builder = new phyphoxXMLBuilder();

    var rootElement = $("#"+rootID);
    rootElement.addClass("phyphoxEditor");
    var tabBar = $("<div id=\"phyphoxEditorTabBar\"></div>");
    var workArea = $("<div id=\"phyphoxEditorWorkArea\"></div>");
    var toolBar = $("<div id=\"phyphoxEditorToolBar\"></div>");

//Interface section

    var interfaceSection = function(title, singleColumn) {
        this.title = title;
        if (singleColumn === true)
            this.element = $("<div class=\"phyphoxEditorInterfaceSectionSingle\"></div>");
        else
            this.element = $("<div class=\"phyphoxEditorInterfaceSection\"></div>");
        this.element.append($("<div class=\"phyphoxEditorInterfaceSectionTitle\">"+title+"</div>"));
        this.content = $("<div class=\"phyphoxEditorInterfaceSectionContent\"></div>");
        this.element.append(this.content);
    }

    interfaceSection.prototype.getRootElement = function () {
        return this.element;
    }

    interfaceSection.prototype.appendContentElement = function (x) {
        this.content.append(x);
    }

//Generic interface element

    var _elementIndex = 0;

    var interfaceElement = function(get, put, label) {
        this.get = get;
        this.put = put;
        this.id = "phyphoxEditorInterfaceElement_" + _elementIndex++;
        this.element = $("<div class=\"phyphoxEditorInterfaceElement\"></div>");
        if (typeof label !== "undefined" && label !== null) {
            label = $("<label for=\"" + this.id + "\">" + label + "</label>");
            this.element.append(label);
        }
    }

    interfaceElement.prototype.getElement = function () {
        return this.element;
    }

    interfaceElement.prototype.refresh = function () {
    }

//Interface element: Button

    var interfaceElementButton = function(get, put, label) {
        interfaceElement.call(this, get, put, label);
        this.input = $("<button id=\"" + this.id + "\"></button>");
        this.input.click(function() {
            put();
        });
        this.refresh();
        this.element.append(this.input);
    }
    interfaceElementButton.prototype = new interfaceElement();
    interfaceElementButton.prototype.constructor = interfaceElementButton;

    interfaceElementButton.prototype.refresh = function () {
        this.input.text(this.get());
    }

//Interface element: String

    var interfaceElementString = function(get, put, label) {
        interfaceElement.call(this, get, put, label);
        this.input = $("<input type=\"text\" id=\"" + this.id + "\" />");
        this.input.change(function() {
            put(this.value);
        });
        this.refresh();
        this.element.append(this.input);
    }
    interfaceElementString.prototype = new interfaceElement();
    interfaceElementString.prototype.constructor = interfaceElementString;

    interfaceElementString.prototype.refresh = function () {
        this.input.val(this.get());
    }

//Interface element: Text

    var interfaceElementText = function(get, put, label) {
        interfaceElement.call(this, get, put, label);
        this.input = $("<textarea rows=\"8\" type=\"text\" id=\"" + this.id + "\"></textarea>");
        this.input.change(function() {
            put(this.value);
        });
        this.refresh();
        this.element.append(this.input);
    }
    interfaceElementText.prototype = new interfaceElement();
    interfaceElementText.prototype.constructor = interfaceElementText;

    interfaceElementText.prototype.refresh = function () {
        this.input.val(this.get());
    }

//Generic tab

    var tab = function(title) {
        this.title = title;
        this.element = $("<div class=\"phyphoxEditorTabRoot\"></div>");
    }

    tab.prototype.getElement = function () {
        return this.element;
    }

    tab.prototype.refresh = function () {
    }

//Main tab

    var tabMain = function() {
        tab.call(this, "Main");

        experimentSection = new interfaceSection("Experiment information");

        this.ieTitle = new interfaceElementString(builder.getTitle, builder.setTitle, "Title");
        experimentSection.appendContentElement(this.ieTitle.getElement());
        this.ieCategory = new interfaceElementString(builder.getCategory, builder.setCategory, "Category");
        experimentSection.appendContentElement(this.ieCategory.getElement());
        this.ieDescription = new interfaceElementText(builder.getDescription, builder.setDescription, "Description");
        experimentSection.appendContentElement(this.ieDescription.getElement());

        this.element.append(experimentSection.getRootElement());
    }
    tabMain.prototype = new tab();
    tabMain.prototype.constructor = tabMain;

    tabMain.prototype.refresh = function () {
        this.ieTitle.refresh();
        this.ieCategory.refresh();
        this.ieDescription.refresh();
    }

//XML tab

    var tabXML = function() {
        tab.call(this, "XML");
        this.code = $("<code></code>");
        codeSection = new interfaceSection("phyphox file content", true);
        codeSection.appendContentElement(this.code);

        this.element.append(codeSection.getRootElement());
    }
    tabXML.prototype = new tab();
    tabXML.prototype.constructor = tabXML;

    tabXML.prototype.refresh = function () {
        this.code.html(builder.generateXML(true));
    }


//End tabs

    var tabs = Array();

    var addTab = function(tab) {
        tabs.push(tab);
        tabButton = $("<div class=\"phyphoxEditorTabButton\">"+tab["title"]+"</div>");
        tabButton.click(function() {
            tab.refresh();
            $(".phyphoxEditorTabRoot").hide();
            $(".phyphoxEditorTabButton").removeClass("active");
            $(this).addClass("active");
            tab.getElement().show();
        });
        tabBar.append(tabButton);
        workArea.append(tab.getElement().hide());
    }

    var recalculateWorkArea = function() {
        workArea.outerHeight(rootElement.height() - tabBar.outerHeight() - toolBar.outerHeight());
    }

    function downloadPhyphoxFile() {
        var element = document.createElement('a');
        element.setAttribute('href', 'data:text/plain;charset=utf-8,' + encodeURIComponent(builder.generateXML(false)));
        element.setAttribute('download', builder.getTitle() + ".phyphox");
        element.style.display = 'none';
        document.body.appendChild(element);
        element.click();
        document.body.removeChild(element);
    }

    rootElement.append(tabBar).append(workArea).append(toolBar);
    addTab(new tabMain());
    addTab(new tabXML());
    toolBar.append(
        new interfaceElementButton(
            function(){return "Load experiment"},
            function(){alert("Not yet implemented.")}
        ).getElement()
    );
    toolBar.append(
        new interfaceElementButton(
            function(){return "Download experiment"},
            function(){downloadPhyphoxFile()}
        ).getElement()
    );
    recalculateWorkArea();
    $(".phyphoxEditorTabButton").get(0).click();
}

