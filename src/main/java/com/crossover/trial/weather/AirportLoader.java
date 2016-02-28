package com.crossover.trial.weather;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import java.io.*;

// CR: Delete the TODO: Comment
/**
 * A simple airport loader which reads a file from disk and sends entries to the webservice
 *
 * TODO: Implement the Airport Loader
 * 
 * @author code test administrator
 */
public class AirportLoader {

    /** end point for read queries */
    private WebTarget query;

    /** end point to supply updates */
    private WebTarget collect;

    public AirportLoader() {
        Client client = ClientBuilder.newClient();
        // CR: Hardcoding values like this is a bad idea. Use a Configuration file where you can change the values
        // CR: if these services are ever moved.
        query = client.target("http://localhost:8080/query");
        collect = client.target("http://localhost:8080/collect");
    }

    public void upload(InputStream airportDataStream) throws IOException{
        // CR: This method doesn't do anything specific. If the idea is to test the code, move it to the tests section.
        BufferedReader reader = new BufferedReader(new InputStreamReader(airportDataStream));
        String l = null;
        while ((l = reader.readLine()) != null) {
            break;
        }
    }

    public static void main(String args[]) throws IOException{
        File airportDataFile = new File(args[0]);
        if (!airportDataFile.exists() || airportDataFile.length() == 0) {
            System.err.println(airportDataFile + " is not a valid input");
            System.exit(1);
        }

        AirportLoader al = new AirportLoader();
        al.upload(new FileInputStream(airportDataFile));
        System.exit(0);
    }
}
