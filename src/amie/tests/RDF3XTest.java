package amie.tests;

import java.sql.DriverManager;
import java.sql.ResultSet;
import de.mpii.rdf3x.Statement;

public class RDF3XTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		java.sql.Connection conn = null;
		String url = "rdf3x:///home/lgalarra/Documents/AssociationRuleMining/Data/yago";
		String driver = "de.mpii.rdf3x.Driver";
		java.util.Properties info = new java.util.Properties();
		info.put("rdf3xembedded", "/home/lgalarra/Documents/AssociationRuleMining/rdf3x-subst/bin/rdf3xembedded");
		
		try {
			Class.forName(driver);
			conn = DriverManager.getConnection(url, info);
			System.out.println("Connected to the database");
			de.mpii.rdf3x.Statement statement = (Statement) conn.createStatement();
			ResultSet rs = (ResultSet) statement.executeQuery("SELECT count ?r WHERE {?s <http://yago-knowledge/resource/happenedIn> ?o . ?o <http://yago-knowledge/resource/isLocatedIn> ?x . ?x ?r ?m }");
			while(rs.next()){
				System.out.println(rs.getString(1) + " - " + rs.getString(2));
			}
			
			conn.close();
			System.out.println("Disconnected from database");
		} catch (Exception e) {
		  e.printStackTrace();
		}
	}

}
