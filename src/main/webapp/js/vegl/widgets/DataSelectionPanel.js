/**
 * A panel for rendering a set of CSWRecords that have been (sub)selected by a bounding box.
 */
Ext.define('vegl.widgets.DataSelectionPanel', {
    extend : 'Ext.grid.Panel',

    alias : 'widget.dataselectionpanel',

    /**
     * Accepts the following:
     * {
     *  region : portal.util.BBox - The selected area (defaults to 0,0,0,0)
     *  cswRecords : portal.csw.CSWRecord[] - The records to display info for.
     * }
     */
    constructor : function(config) {
        if (!config.region) {
            config.region = Ext.create('portal.util.BBox', {});
        }
        this.region = config.region;

        if (!config.cswRecords) {
            config.cswRecords = [];
        }
        
        var dataItems = vegl.widgets.DataSelectionPanelRow.parseCswRecords(config.cswRecords, this.region);

        var groupingFeature = Ext.create('Ext.grid.feature.Grouping',{
            groupHeaderTpl: '{name} ({[values.rows.length]} {[values.rows.length > 1 ? "Items" : "Item"]})'
        });
        
        var hideHeaders = true;
        if (typeof(config.hideHeaders) !== 'undefined' && config.hideHeaders != null) {
            hideHeaders = config.hideHeaders;
        }

        //Build our configuration object
        Ext.apply(config, {
            selModel : Ext.create('Ext.selection.CheckboxModel', {}),
            features : [groupingFeature],
            store : Ext.create('Ext.data.Store', {
                groupField : 'resourceType',
                model : 'vegl.widgets.DataSelectionPanelRow',
                data : dataItems
            }),
            hideHeaders : hideHeaders,
            plugins : [{
                ptype : 'selectablegrid'
            }],
            columns: [{
                //Title column
                dataIndex: 'name',
                flex: 1,
                renderer: Ext.bind(this._nameRenderer, this)
            },{
                dataIndex: 'description',
                width: 280,
                renderer: Ext.bind(this._descriptionRenderer, this)
            },{
                xtype: 'actioncolumn',
                width:30,
                sortable: false,
                items: [{
                    icon: 'img/edit.png',
                    tooltip: 'Edit the download options',
                    scope : this,
                    handler: function(grid, rowIndex, colIndex, item, e, record) {
                        this._handleRowEdit(record);
                    }
                }]
            }]
        });

        this.callParent(arguments);

        //Update the descriptions of all layers now they have been added
        for (var i = 0; i < dataItems.length; i++) {
            this._updateDescription(dataItems[i]);
        }
    },

    /**
     * Handles the editing of a row by showing a popup and then updating the row description upon completion
     */
    _handleRowEdit : function(dataItem) {
        var or = dataItem.get('onlineResource');
        var dlOptions = dataItem.get('downloadOptions');

        //Depending on the resource type, open an appropriate edit window
        switch (or.get('type')) {
        case portal.csw.OnlineResource.WCS:
            var popup = Ext.create('Ext.window.Window', {
                layout : 'fit',
                width : 700,
                height : 400,
                modal : true,
                title : 'Subset of ' + dataItem.get('name'),
                items : [{
                    xtype : 'erddapsubsetpanel',
                    itemId : 'subset-panel',
                    region : Ext.create('portal.util.BBox', dlOptions),
                    coverageName : dlOptions.layerName,
                    coverageUrl : or.get('url'),
                    name : dlOptions.name,
                    localPath : dlOptions.localPath,
                    description : dlOptions.description,
                    dataType : dlOptions.format
                }],
                buttons : [{
                    text : 'Save Changes',
                    iconCls : 'add',
                    align : 'right',
                    scope : this,
                    handler : function(btn) {
                        var parentWindow = btn.findParentByType('window');
                        var panel = parentWindow.getComponent('subset-panel');

                        var params = panel.getForm().getValues();

                        //The ERDDAP subset panel doesn't use coverage name for anything but size estimation
                        //Therefore we need to manually preserve it ourselves
                        params.layerName = dlOptions.layerName;

                        dataItem.set('downloadOptions', params);
                        this._updateDescription(dataItem);

                        parentWindow.close();
                    }
                }]
            });
            popup.show();
            break;
        default:
            var popup = Ext.create('Ext.window.Window', {
                layout : 'fit',
                width : 700,
                height : 400,
                modal : true,
                title : 'Download from ' + dataItem.get('name'),
                items : [{
                    xtype : 'filedownloadpanel',
                    itemId : 'download-panel',
                    region : Ext.create('portal.util.BBox', dlOptions),
                    url : dlOptions.url,
                    localPath : dlOptions.localPath,
                    name : dlOptions.name,
                    description : dlOptions.description,
                }],
                buttons : [{
                    text : 'Save Changes',
                    iconCls : 'add',
                    align : 'right',
                    scope : this,
                    handler : function(btn) {
                        var parentWindow = btn.findParentByType('window');
                        var panel = parentWindow.getComponent('download-panel');
                        var params = panel.getForm().getValues();

                        dataItem.set('downloadOptions', params);
                        this._updateDescription(dataItem);

                        parentWindow.close();
                    }
                }]
            });
            popup.show();
            break;
        }
    },

    /**
     * Saves every single selected row in this panel to the user's session. Errors are reported
     * via a callback function
     *
     * @param callback function(totalSelected, totalErrors)
     */
    saveCurrentSelection: function(callback) {
        var sm = this.getSelectionModel();
        var selectedRows = sm.getSelection();

        if (selectedRows.length === 0) {
            callback(0, 0);
            return;
        }


        var totalResponses = 0;
        var totalErrors = 0;

        var myMask = new Ext.LoadMask(this, {msg: "Saving selected datasets..."});
        myMask.show();

        //Simple handler for keeping track of how many requests we've sent vs how many responses we've received
        var responseHandler = function(success) {
            totalResponses++;
            if (!success) {
                totalErrors++;
            }

            if (totalResponses >= selectedRows.length) {
                myMask.hide();
                callback(totalResponses, totalErrors);
            }
        };

        //Different data sources have different functions to save
        for (var i = 0; i < selectedRows.length; i++) {
            this._saveRowIntoSession(selectedRows[i], responseHandler);
        }
    },

    /**
     * Saves a vegl.widgets.DataSelectionPanelRow into the user session
     */
    _saveRowIntoSession : function(row, responseHandler) {
        switch (row.get('onlineResource').get('type')) {
        case portal.csw.OnlineResource.WCS:
            Ext.Ajax.request({
                url : 'addErddapRequestToSession.do',
                params : row.get('downloadOptions'),
                callback : function(options, success, response) {
                    responseHandler(success);
                }
            });
            break;
        default:
            Ext.Ajax.request({
                url : 'addDownloadRequestToSession.do',
                params : row.get('downloadOptions'),
                callback : function(options, success, response) {
                    responseHandler(success);
                }
            });
            break;
        }
    },

    /**
     * Updates the description text for the specified dataitem. The update may occur asynchronously
     */
    _updateDescription : function(dataItem) {
        var or = dataItem.get('onlineResource');
        var dlOptions = dataItem.get('downloadOptions');

        // WCS resources have their description based on the selected region (whatever that may be)
        if (or.get('type') === portal.csw.OnlineResource.WCS) {

            dataItem.set('description', 'Loading size details...');
            vegl.util.WCSUtil.estimateCoverageSize(dlOptions, or.get('url'), dlOptions.layerName, dataItem, function(success, errorMsg, response, dataItem) {
                if (!success) {
                    dataItem.set('description', errorMsg);
                } else {
                    var approxTotal = Ext.util.Format.number(response.roundedTotal, '0,000');
                    var approxSize = Ext.util.Format.fileSize(vegl.util.WCSUtil.roundToApproximation(response.width * response.height * 4));
                    dataItem.set('description', Ext.util.Format.format('Approximately <b>{0}</b> data points in total.<br>Uncompressed that\'s roughly {1}', approxTotal, approxSize));
                }
            });
        } else {
            //Otherwise the description is pulled direct from the online resource
            dataItem.set('description', or.get('description'));
        }
    },

    _nameRenderer : function(value, metaData, record, row, col, store, gridView) {
        var name = record.get('name');
        var url = record.get('onlineResource').get('url');

        //Ensure there is a title (even it is just '<Untitled>'
        if (!name || name.length === 0) {
            name = '&gt;Untitled&lt;';
        }

        //Render our HTML
        return Ext.DomHelper.markup({
            tag : 'div',
            children : [{
                tag : 'b',
                html : name
            },{
                tag : 'br'
            },{
                tag : 'span',
                style : {
                    color : '#555'
                },
                children : [{
                    html : url
                }]
            }]
        });
    },

    _descriptionRenderer : function(value, metaData, record, row, col, store, gridView) {
        var type = record.get('onlineResource').get('type');
        var description = record.get('description');

        //Render our HTML
        switch(type) {
        case portal.csw.OnlineResource.WWW:
        case portal.csw.OnlineResource.FTP:
        case portal.csw.OnlineResource.IRIS:
        case portal.csw.OnlineResource.UNSUPPORTED:
            return Ext.DomHelper.markup({
                tag : 'div',
                children : [{
                    tag : 'a',
                    target : '_blank',
                    href : record.get('onlineResource').get('url'),
                    children : [{
                        tag : 'span',
                        html : 'Click here to download.'
                    }]
                }]
            });
        default:
            return description;
        }
    }
});

Ext.define('vegl.widgets.DataSelectionPanelRow', {
    extend : 'Ext.data.Model',

    statics : {
        /**
         * Parses a single csw record and parent layer into an array of DataSelectionPanelRow items
         */
        parseCswRecord : function(cswRecord, defaultBbox) {
            var dataItems = [];
            var resources = cswRecord.get('onlineResources');
            for (var i = 0; i < resources.length; i++) {
                var onlineResource = resources[i];

                //Set the defaults of our new item
                newItem = {
                    resourceType : portal.csw.OnlineResource.typeToString(onlineResource.get('type')),
                    name : onlineResource.get('name'),
                    description : onlineResource.get('description'),
                    selected : true,
                    cswRecord : cswRecord,
                    onlineResource : onlineResource,
                    downloadOptions : {
                        name : 'Subset of ' + onlineResource.get('name'),
                        description : onlineResource.get('description'),
                        url : onlineResource.get('url'),
                        localPath : '/tmp/' + onlineResource.get('name'),
                        eastBoundLongitude : (defaultBbox ? defaultBbox.eastBoundLongitude : 0),
                        northBoundLatitude : (defaultBbox ? defaultBbox.northBoundLatitude : 0),
                        southBoundLatitude : (defaultBbox ? defaultBbox.southBoundLatitude : 0),
                        westBoundLongitude : (defaultBbox ? defaultBbox.westBoundLongitude : 0)
                    }
                };

                //Add/subtract info based on resource type
                switch(onlineResource.get('type')) {
                case portal.csw.OnlineResource.WCS:
                    newItem.description = 'Loading size details...';
                    delete newItem.downloadOptions.url;
                    newItem.downloadOptions.format = 'nc';
                    newItem.downloadOptions.layerName = newItem.name;
                    break;
                case portal.csw.OnlineResource.WWW:
                    break;

                //We don't support EVERY type
                default:
                    continue;
                }

                dataItems.push(Ext.create('vegl.widgets.DataSelectionPanelRow', newItem));
            }

            var childCswRecords = cswRecord.get('childRecords');
            if (childCswRecords) {
                for (var i = 0; i < childCswRecords.length; i++) {
                    dataItems = dataItems.concat(vegl.widgets.DataSelectionPanelRow.parseCswRecord(childCswRecords[i], defaultBbox));
                }
            }

            return dataItems;
        },

        /**
         * Parses a CSWRecord array into an array of simple JS objects for usage with the internal data store for this panel
         */
        parseCswRecords : function(cswRecords, defaultBbox) {
            var dataItems = [];
            for (var i = 0; i < cswRecords.length; i++) {
                var cswRecord = cswRecords[i];

                dataItems = dataItems.concat(vegl.widgets.DataSelectionPanelRow.parseCswRecord(cswRecord, defaultBbox));
          }

          return dataItems;
        }
    },

    fields: [
             {name : 'layerName', type: 'string'},
             {name : 'resourceType', type: 'string'},
             {name : 'name', type: 'string'},
             {name : 'description', type: 'string'},
             {name : 'selected', type: 'boolean'},
             {name : 'onlineResource', type: 'auto'},
             {name : 'cswRecord', type: 'auto'},
             {name : 'downloadOptions', type: 'auto'}
    ]



});