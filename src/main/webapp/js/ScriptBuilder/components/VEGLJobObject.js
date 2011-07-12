/*
 * This file is part of the AuScope Virtual Exploration Geophysics Lab (VEGL) project.
 * Copyright (c) 2011 CSIRO Earth Science and Resource Engineering
 *
 * Licensed under the terms of the GNU Lesser General Public License.
 */
Ext.namespace("ScriptBuilder");
Ext.ux.ComponentLoader.load( {url : ScriptBuilder.componentPath + "VEGLJobObject.json"});

VEGLJobObjectNode = Ext.extend(ScriptBuilder.BaseComponent, {

    constructor : function(container) {
        VEGLJobObjectNode.superclass.constructor.apply(this, [ container,
                "VEGL Job Object", "VEGLJobObject", "s" ]);

        var numShells = container.getShellCommands().length;
        this.values.uniqueName = "shell" + numShells;
    },
    
    /**
     * The tab string used for generating our python script
     */
    pTab : '    ',
    
    /**
     * The new line string used for generating our python script
     */
    pNewLine : '\n',
    
    /**
     * Capitalises the first character of s
     */
    _capitaliseFirst : function(s) {
        if (s.length == 0) {
            return '';
        }
        
        return s.charAt(0).toUpperCase() + s.substring(1);
    },
    
    /**
     * Generates a a Python snippet for a function that will return
     * a particular 
     */
    _getPrimitiveFunction : function(fieldName, value, baseIndent) {
        var functionText = '';
        
        functionText += baseIndent + 'def get' + this._capitaliseFirst(fieldName) + '(self):' + this.pNewLine;
        
        //Ext JS forms can't return non string values so we need to be very sure we dont have
        //and integer/float encoded as a string
        var isString = Ext.isString(value);
        if (isString && value.length > 0) {
            if (value.match(/[0-9]/i)) {
                isString = isNaN(parseFloat(value));
            }
        }
        
        if (isString) {
            functionText += baseIndent + this.pTab + 'return \'' + value + '\'' + this.pNewLine;
        } else {
            functionText += baseIndent + this.pTab + 'return ' + value + '' + this.pNewLine;
        }
        functionText += this.pNewLine;
        
        
        return functionText;
    },
    
    /**
     * This is where we dynamically generate a python Getter/Setter class from the job object that
     * is sent to us 
     */
    getScript : function() {
        var classText = '';
        
        classText += '# Autogenerated Python Getter/Setter class' + this.pNewLine;
        classText += 'class VEGLParameters:' + this.pNewLine;
        
        //Iterate our fields generating getter methods
        for (var field in this.values) {
            var value = this.values[field];
            if (Ext.isFunction(value)) {
                
            } else if (Ext.isObject(value)) {
                //Ignore complex fields for the moment
            } else if (Ext.isPrimitive(value)) {
                //Primitive fields turn into 'Getters'
                classText += this._getPrimitiveFunction(field, value, this.pTab);
            }
        }
        
        classText += this.pNewLine;
        
        return classText;
    }
});
