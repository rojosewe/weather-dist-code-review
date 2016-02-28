package com.crossover.trial.weather;

import com.google.gson.Gson;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.logging.Logger;

/**
 * The Weather App REST endpoint allows clients to query, update and check health stats. Currently, all data is
 * held in memory. The end point deploys to a single container
 *
 * @author code test administrator
 */
@Path("/query")
public class RestWeatherQueryEndpoint implements WeatherQueryEndpoint {

    public final static Logger LOGGER = Logger.getLogger("WeatherQuery");

    /** earth radius in KM */
    public static final double R = 6372.8;

    /** shared gson json to object factory */
    public static final Gson gson = new Gson();

    // CR: These structures could be better off in a data provider class and leave the endpoint class only to manage
    // CR: the API access to information. The API can access more centralized information and handle less responsibility.

    /** all known airports */
    protected static List<AirportData> airportData = new ArrayList<>();


    // CR: This information is accessed almolst solely by index. LinkedLists are not very efficient for that. Try instead an ArrayList or
    // CR: event better, it should be a map associated to the corresponding IATA. 
    /** atmospheric information for each airport, idx corresponds with airportData */
    protected static List<AtmosphericInformation> atmosphericInformation = new LinkedList<>();

    /**
     * Internal performance counter to better understand most requested information, this map can be improved but
     * for now provides the basis for future performance optimizations. Due to the stateless deployment architecture
     * we don't want to write this to disk, but will pull it off using a REST request and aggregate with other
     * performance metrics {@link #ping()}
     */
    public static Map<AirportData, Integer> requestFrequency = new HashMap<AirportData, Integer>();

    public static Map<Double, Integer> radiusFreq = new HashMap<Double, Integer>();

    /**
     * Retrieve service health including total size of valid data points and request frequency information.
     *
     * @return health stats for the service as a string
     */
    @GET
    @Path("/ping")
    public String ping() {
        Map<String, Object> retval = new HashMap<>();

        // CR: I get a bit lost here, why not split this method into three different methods. getDataSize, get Freq and getRadiusFreq?
        int datasize = 0;
        for (AtmosphericInformation ai : atmosphericInformation) {
            // we only count recent readings
            if (ai.getCloudCover() != null
                || ai.getHumidity() != null
                || ai.getPressure() != null
                || ai.getPrecipitation() != null
                || ai.getTemperature() != null
                || ai.getWind() != null) {
                // updated in the last day
                if (ai.getLastUpdateTime() > System.currentTimeMillis() - 86400000) {
                    datasize++;
                }
            }
        }
        retval.put("datasize", datasize);

        Map<String, Double> freq = new HashMap<>();
        // fraction of queries
        for (AirportData data : airportData) {
            double frac = (double)requestFrequency.getOrDefault(data, 0) / requestFrequency.size();
            freq.put(data.getIata(), frac);
        }
        retval.put("iata_freq", freq);

        int m = radiusFreq.keySet().stream()
                .max(Double::compare)
                .orElse(1000.0).intValue() + 1;

        int[] hist = new int[m];
        for (Map.Entry<Double, Integer> e : radiusFreq.entrySet()) {
            int i = e.getKey().intValue() % 10;
            hist[i] += e.getValue();
        }
        retval.put("radius_freq", hist);

        return gson.toJson(retval);
    }

    /**
     * Given a query in json format {'iata': CODE, 'radius': km} extracts the requested airport information and
     * return a list of matching atmosphere information.
     *
     * @param iata the iataCode
     * @param radiusString the radius in km
     *
     * @return a list of atmospheric information
     */
    @GET
    @Path("/weather/{iata}/{radius}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@PathParam("iata") String iata, @PathParam("radius") String radiusString) {
        double radius = radiusString == null || radiusString.trim().isEmpty() ? 0 : Double.valueOf(radiusString);
        updateRequestFrequency(iata, radius);

        List<AtmosphericInformation> retval = new ArrayList<>();
        if (radius == 0) {
            int idx = getAirportDataIdx(iata);
            // CR: You need to handle the case where getAirportDataIdx doesn't find the iata. Send a 404 response
            retval.add(atmosphericInformation.get(idx));
        } else {
            AirportData ad = findAirportData(iata);
            // CR: You need to handle the case where getAirportDataIdx doesn't find the iata. Send a 404 response
            for (int i=0;i< airportData.size(); i++){
                if (calculateDistance(ad, airportData.get(i)) <= radius){
                    AtmosphericInformation ai = atmosphericInformation.get(i);
                    // CR: This long checkup is common. What about moving it as a method of the class AtmosphericInformation?
                    if (ai.getCloudCover() != null || ai.getHumidity() != null || ai.getPrecipitation() != null
                       || ai.getPressure() != null || ai.getTemperature() != null || ai.getWind() != null){
                        retval.add(ai);
                    }
                }
            }
        }
        return Response.status(Response.Status.OK).entity(retval).build();
    }


    // CR: This method shouldn't be the responsibility of the endpoint. Move it t the Data Provider class you shoukd create.
    /**
     * Records information about how often requests are made
     *
     * @param iata an iata code
     * @param radius query radius
     */
    public void updateRequestFrequency(String iata, Double radius) {
        AirportData airportData = findAirportData(iata);
        requestFrequency.put(airportData, requestFrequency.getOrDefault(airportData, 0) + 1);
        radiusFreq.put(radius, radiusFreq.getOrDefault(radius, 0));
    }

    // CR: This method shouldn't be the responsibility of the endpoint. Move it t the Data Provider class you shoukd create.
    // CR: Instead of returning a null value that someone could forget to check for, why not create an AirportNotFoundException?
    /**
     * Given an iataCode find the airport data
     *
     * @param iataCode as a string
     * @return airport data or null if not found
     */
    public static AirportData findAirportData(String iataCode) {
        return airportData.stream()
            .filter(ap -> ap.getIata().equals(iataCode))
            .findFirst().orElse(null);
    }

    // CR: Could the airport data structure be moved to a data provider class. Other endpoints access this information.
    // CR: Instead of returning a -1 that someone could forget to check for, why not create an AirportNotFoundException?
    /**
     * Given an iataCode find the airport data
     *
     * @param iataCode as a string
     * @return airport data or null if not found
     */
    public static int getAirportDataIdx(String iataCode) {
        AirportData ad = findAirportData(iataCode);
        return airportData.indexOf(ad);
    }

    // CR: This method shouldn't be the responsibility of the endpoint. Move it t the Data Provider class you should create.
    /**
     * Haversine distance between two airports.
     *
     * @param ad1 airport 1
     * @param ad2 airport 2
     * @return the distance in KM
     */
    public double calculateDistance(AirportData ad1, AirportData ad2) {
        // CR: Use the getters and setters, instead of the field directly
        double deltaLat = Math.toRadians(ad2.latitude - ad1.latitude);
        double deltaLon = Math.toRadians(ad2.longitude - ad1.longitude);
        double a =  Math.pow(Math.sin(deltaLat / 2), 2) + Math.pow(Math.sin(deltaLon / 2), 2)
                * Math.cos(ad1.latitude) * Math.cos(ad2.latitude);
        double c = 2 * Math.asin(Math.sqrt(a));
        return R * c;
    }
}
