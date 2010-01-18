package org.auscope.portal.server.web.service;

import org.auscope.portal.server.web.service.HttpServiceCaller;
import org.auscope.portal.server.web.IWFSGetFeatureMethodMaker;
import org.auscope.portal.server.web.WFSGetFeatureMethodMakerPOST;
import org.auscope.portal.mineraloccurrence.*;

import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * A utility class which provides methods for querying a mineral occurence service
 *
 * @version $Id$
 */
@Service
public class MineralOccurrenceService {
   
    protected final Log log = LogFactory.getLog(getClass());
    
    private HttpServiceCaller httpServiceCaller;
    private MineralOccurrencesResponseHandler mineralOccurrencesResponseHandler;
    private IWFSGetFeatureMethodMaker methodMaker;

    /**
     * Initialise
     */
    public MineralOccurrenceService() {
        this.httpServiceCaller = new HttpServiceCaller();
        this.mineralOccurrencesResponseHandler = new MineralOccurrencesResponseHandler();
        this.methodMaker = new WFSGetFeatureMethodMakerPOST();
    }

    public MineralOccurrenceService(HttpServiceCaller httpServiceCaller, MineralOccurrencesResponseHandler mineralOccurrencesResponseHandler, IWFSGetFeatureMethodMaker methodMaker) {
        this.httpServiceCaller = httpServiceCaller;
        this.mineralOccurrencesResponseHandler = mineralOccurrencesResponseHandler;
        this.methodMaker = methodMaker;
    }

    /**
     * Get all the mines from a given service url and return them as Mine objects
     *
     * @param serviceURL - the service to get all of the mines from
     * @return a collection (List) of mine nodes 
     * @throws Exception
     */
    public List<Mine> getAllMines(String serviceURL) throws Exception {
        //get the mines
        String mineResponse = this.getAllMinesGML(serviceURL);

        //convert the response into a nice collection of Mine Nodes
        List<Mine> mines = this.mineralOccurrencesResponseHandler.getMines(mineResponse);

        //send it back!
        return mines;
    }

    /**
     * Get all the mines from a given service url and return the response
     * @param serviceURL
     * @return
     * @throws Exception
     */
    public String getAllMinesGML(String serviceURL) throws Exception {
        //create a GetFeature request with an empty filter - get all
        HttpMethodBase method = methodMaker.makeMethod(serviceURL, "er:Mine", "");

        //call the service, and get all the mines
        return httpServiceCaller.getMethodResponseAsString(method, httpServiceCaller.getHttpClient());
    }

    /**
     * Given a specific service and a mineName, get that mine from the service
     * @param serviceURL - the service to get the mine from
     * @param mineName - the name of the mine to get
     * @return
     */
    public List<Mine> getMineWithSpecifiedName(String serviceURL, String mineName) throws Exception {
        //get the mine
        String mineResponse = this.getMineWithSpecifiedNameGML(serviceURL, mineName);

        //convert the response into updateCSWRecords nice collection of Mine Nodes
        List<Mine> mines = this.mineralOccurrencesResponseHandler.getMines(mineResponse);

        //send it back!
        return mines;
    }

    /**
     * Given a specific service and a mineName, get that mine from the service
     *
     * @param serviceURL
     * @param mineName
     * @return
     * @throws Exception
     */
    public String getMineWithSpecifiedNameGML(String serviceURL, String mineName) throws Exception {
        //create a filter for the specified name
        MineFilter mineFilter = new MineFilter(mineName);

        log.debug(serviceURL + "\n" + mineFilter.getFilterString());

        //create a GetFeature request with filter constraints on a query
        HttpMethodBase method = methodMaker.makeMethod(serviceURL, "er:Mine", mineFilter.getFilterString());

        //call the service, and get all the mines
        return httpServiceCaller.getMethodResponseAsString(method, httpServiceCaller.getHttpClient());
    }

    /**
     * Returns commodities based on a given service, the commodityGroup, and acommodityName
     *
     * If both commodityGroup and commodityName are empty strings a GetALL query will be run
     *
     * @param commodityGroup
     * @param commodityName
     * @return
     */
    public Collection<Commodity> getCommodity(String serviceURL, String commodityGroup, String commodityName) throws Exception {
        //httpclient method
        HttpMethodBase method = null;

        //if we don't have a name or a group, then just get all of them
        if(commodityGroup.equals("") && commodityName.equals("")) {
            method = methodMaker.makeMethod(serviceURL, "er:Commodity", "");
        } else {
            //create the filter to append to the url
            CommodityFilter commodityFilter = new CommodityFilter(commodityGroup, commodityName);
            log.debug(serviceURL + "\n" + commodityFilter.getFilterString());
            
            //create a GetFeature request with filter constraints on a query
            method = methodMaker.makeMethod(serviceURL, "er:Commodity", commodityFilter.getFilterString());
        }

        //call the service, and get all the commodities
        String commodityResponse = httpServiceCaller.getMethodResponseAsString(method, httpServiceCaller.getHttpClient());

        //parse the commodites and return them
        return this.mineralOccurrencesResponseHandler.getCommodities(commodityResponse);
    }

    /**
     * Given a list of parameters, call a service and get the Mineral Occurrence GML
     * @param serviceURL
     * @param commodityName
     * @param commodityGroup
     * @param measureType
     * @param minOreAmount
     * @param minOreAmountUOM
     * @param minCommodityAmount
     * @param minCommodityAmountUOM
     * @param cutOffGrade
     * @param cutOffGradeUOM
     * @return
     */
    public String getMineralOccurrenceGML( String serviceURL,
            String commodityName,
            String commodityGroup,
            String measureType,
            String minOreAmount,
            String minOreAmountUOM,
            String minCommodityAmount,
            String minCommodityAmountUOM,
            String cutOffGrade,
            String cutOffGradeUOM) throws Exception {

        //get the commodities, we need their URI's to do a min occ query
        Collection<Commodity> commodities = this.getCommodity(serviceURL, commodityGroup, commodityName);

        //if there are no commodities we can't continue
        if(commodities.size() == 0)
                return "";

        //create the mineral occurrence filter
        MineralOccurrenceFilter mineralOccurrenceFilter = new MineralOccurrenceFilter(  commodities,
                                                                                        measureType,
                                                                                        minOreAmount,
                                                                                        minOreAmountUOM,
                                                                                        minCommodityAmount,
                                                                                        minCommodityAmountUOM,
                                                                                        cutOffGrade,
                                                                                        cutOffGradeUOM);
        log.debug(serviceURL + "\n" + mineralOccurrenceFilter.getFilterString());
        
        //create the method
        HttpMethodBase method = methodMaker.makeMethod(serviceURL, "er:MineralOccurrence", mineralOccurrenceFilter.getFilterString());

        //run the dam query
        return httpServiceCaller.getMethodResponseAsString(method, httpServiceCaller.getHttpClient());
    }

    public String getMiningActivityGML( String serviceURL,
                                        List<Mine> mines,
                                        String startDate,
                                        String endDate,
                                        String oreProcessed,
                                        String producedMaterial,
                                        String cutOffGrade,
                                        String production) throws Exception {
        if(mines.size() == 0)
                return "";
        
        //create the filter
        MiningActivityFilter miningActivityFilter = new MiningActivityFilter(mines, startDate, endDate, oreProcessed, producedMaterial, cutOffGrade, production);
        log.debug(serviceURL + "\n" + miningActivityFilter.getFilterString());
        
        //create the method
        HttpMethodBase method = methodMaker.makeMethod(serviceURL, "er:MiningActivity", miningActivityFilter.getFilterString());

        //run dat query
        return this.httpServiceCaller.getMethodResponseAsString(method, httpServiceCaller.getHttpClient());
    }
}