package com.urremote.classifier.fusiontables;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import android.content.Context;
import android.util.Log;
import au.com.bytecode.opencsv.CSVReader;

import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.MethodOverride;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.util.Strings;
import com.urremote.classifier.auth.AuthManager;
import com.urremote.classifier.common.Constants;
import com.urremote.classifier.gdata.GDataWrapper;
import com.urremote.classifier.gdata.GDataWrapper.QueryFunction;

public class FusionTables {
	private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

	/** The GData service id for Fusion Tables. */
	public static final String SERVICE_ID = "fusiontables";

	// /** The path for viewing a map visualization of a table. */
	// private static final String FUSIONTABLES_MAP =
	// "https://www.google.com/fusiontables/embedviz?"
	// + "viz=MAP&q=select+col0,+col1,+col2,+col3+from+%s+&h=false&"
	// + "lat=%f&lng=%f&z=%d&t=1&l=col2";

	/** Standard base feed url for Fusion Tables. */
	private static final String FUSIONTABLES_BASE_FEED_URL = "https://www.google.com/fusiontables/api/query";

	// private static final int MAX_POINTS_PER_UPLOAD = 2048;

	private static final String GDATA_VERSION = "2";
	
	private static final String APP_NAME = "Test Fusion Tables";

	private static final HttpTransport transport = new ApacheHttpTransport();
	private static final HttpRequestFactory httpRequestFactory = transport
			.createRequestFactory(new MethodOverride());
	
	public static class Column {
		String id;
		String name;
		String dataType;
		String viewName;
		
		public Column(String id, String name, String dataType, String viewName) {
			this.id = id;
			this.name = name;
			this.dataType = dataType;
			this.viewName = viewName;
		}
		
		public Column(String id, String name, String dataType) {
			this(id,name,dataType,null);
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Column) {
				Column other = (Column)o;
				return //this.id.equalsIgnoreCase(other.id) &&
						this.name.equalsIgnoreCase(other.name) &&
						this.dataType.equalsIgnoreCase(other.dataType);
			} else
				return super.equals(o);
		}
		
		
	}
	
	public static class Table {
		String id;
		String name;
		List<Column> columns;
		
		public Table(String id, String name) {
			this.id = id;
			this.name = name;
			this.columns = new ArrayList<FusionTables.Column>();
		}

		public Table(String id, String name, List<Column> columns) {
			this.id = id;
			this.name = name;
			this.columns = new ArrayList<FusionTables.Column>(columns);
		}
		
		@Override
		public boolean equals(Object o) {
			if (o instanceof Table) {
				Table other = (Table)o;
				return //this.id.equalsIgnoreCase(other.id) &&
						this.name.equalsIgnoreCase(other.name) &&
						this.columns.equals(other.columns);
			}
			return super.equals(o);
		}
		
	}
	
	protected Context context;
	protected AuthManager auth;

	public FusionTables(Context context, AuthManager auth) {
		this.context = context;
		this.auth = auth;
	}
	
	public List<Table> retrieveTables() {
		final String query = "SHOW TABLES";
		List<String[]> results = new ArrayList<String[]>();
		if (runQuery(false, query, results)) {
			List<Table> tables = new ArrayList<Table>();
			if (results.size()>2 && results.get(0).length==2
					&& results.get(0)[0].equalsIgnoreCase("table id")
					&& results.get(0)[1].equalsIgnoreCase("name")) {
				for (int i=1; i<results.size(); ++i) {
					tables.add(new Table(results.get(i)[0], results.get(i)[1]));
				}
			}
			return tables;
		} else {
			return null;
		}
	}
	
	public List<Column> retrieveTableColumns(String tableId) {
		final String query = "DESCRIBE "+tableId;
		List<String[]> results = new ArrayList<String[]>();
		if (runQuery(false, query, results)) {
			List<Column> columns = new ArrayList<FusionTables.Column>();
			if (results.size()>2 && results.get(0).length==3
					&& results.get(0)[0].equalsIgnoreCase("column id")
					&& results.get(0)[1].equalsIgnoreCase("name")
					&& results.get(0)[2].equalsIgnoreCase("type")
					) {
				for (int i=1; i<results.size(); ++i) {
					columns.add(new Column(results.get(i)[0], results.get(i)[1], results.get(i)[2]));
				}
			}
			return columns;
		} else {
			return null;
		}
	}
	
	public Table createTable(Table table) {
		StringBuilder query = new StringBuilder(1024);
		query.append("CREATE TABLE '").append(table.name).append("' (");
		boolean first = true;
		for (Column col:table.columns) {
			if (first) {
				first = false;
			} else {
				query.append(",");
			}
			query.append("'").append(col.name).append("':").append(col.dataType);
		}
		query.append(")");
		
		List<String[]> results = new ArrayList<String[]>();
		if (runQuery(true, query.toString(), results)) {
			if (results.size()==2 && results.get(0).length==1 && results.get(0)[0].equalsIgnoreCase("tableid")) {
				String id = results.get(1)[0];
				return new Table(id, table.name, retrieveTableColumns(id));
			}
		}
		
		return null;
	}
	
	public Table createView(String name, String refTableId, Table tableDesc) {
		StringBuilder query = new StringBuilder(1024);
		query.append("CREATE VIEW '").append(name).append("' AS (SELECT ");
		boolean first = true;
		for (Column col:tableDesc.columns) {
			if (col.viewName!=null) {
				if (first) {
					first = false;
				} else {
					query.append(", ");
				}
				query.append("'").append(col.name).append("' AS '").append(col.viewName).append("'");
			}
		}
		query.append("FROM "+refTableId+")");
		
		List<String[]> results = new ArrayList<String[]>();
		if (runQuery(true, query.toString(), results)) {
			if (results.size()==2 && results.get(0).length==1 && results.get(0)[0].equalsIgnoreCase("tableid")) {
				String id = results.get(1)[0];
				return new Table(id, name, retrieveTableColumns(id));
			}
		}
		
		return null;
	}
	
	/**
	 * Runs a query. Handles authentication.
	 * 
	 * @param query
	 *            The given SQL like query
	 * @return true in case of success
	 */
	public boolean runQuery(final boolean usePost, final String query, final List<String[]> results) {
		if (auth.getAuthToken() == null) {
			Log.e(Constants.TAG, "Attempting a query when no auth-token available");
			return false;
		}

		GDataWrapper<HttpRequestFactory> wrapper = new GDataWrapper<HttpRequestFactory>();
		wrapper.setAuthManager(auth);
		wrapper.setRetryOnAuthFailure(false);
		wrapper.setClient(httpRequestFactory);
		wrapper.runQuery(new QueryFunction<HttpRequestFactory>() {
			public void query(HttpRequestFactory factory) throws IOException,
					GDataWrapper.ParseException, GDataWrapper.HttpException,
					GDataWrapper.AuthenticationException {
				GenericUrl url = new GenericUrl(FUSIONTABLES_BASE_FEED_URL);
				url.set("encid", Boolean.toString(true));
				Log.d(Constants.TAG, "Fusion Table Query: "+query);

				HttpRequest request;
				if (usePost) {
					String encodedQuery = URLEncoder.encode(query, "UTF-8");
					ByteArrayInputStream inputStream = new ByteArrayInputStream(
							Strings.toBytesUtf8("sql="+encodedQuery));
					InputStreamContent isc = new InputStreamContent(null,
							inputStream);
					request = factory.buildPostRequest(url, isc);
				} else {
					url.set("sql", query); // no need to encode, GenericUrl class encodes
					request = factory.buildGetRequest(url);
				}
				
				GoogleHeaders headers = new GoogleHeaders();
				headers.setApplicationName(APP_NAME);
				headers.gdataVersion = GDATA_VERSION;
				headers.setGoogleLogin(auth.getAuthToken());
				if (usePost) {
					headers.setContentType(CONTENT_TYPE);
				} else {
					headers.setContentType("text/plain");
				}
				
				request.setHeaders(headers);

				Log.d(Constants.TAG, "Running query: " + url.toString());
				HttpResponse response;
				try {
					response = request.execute();
				} catch (HttpResponseException e) {
					throw new GDataWrapper.HttpException(e.getResponse()
							.getStatusCode(), e.getResponse()
							.getStatusMessage());
				}
				boolean success = response.isSuccessStatusCode();
				if (success) {
					BufferedReader bufferedStreamReader =
							new BufferedReader(new InputStreamReader(response.getContent(), "UTF8"));
					CSVReader reader = new CSVReader(bufferedStreamReader);
					List<String[]> csvLines = reader.readAll();
					
					Log.d(Constants.TAG, "Query Response: ("+csvLines.size()+" lines)");
					for (String[] line:csvLines) {
						Log.d(Constants.TAG, "\t"+Arrays.toString(line));
					}
					
					if (results!=null) {
						results.clear();
						results.addAll(csvLines);
					}
				} else {
					Log.d(Constants.TAG,
							"Query failed: " + response.getStatusMessage()
									+ " (" + response.getStatusCode() + ")");
					throw new GDataWrapper.HttpException(response
							.getStatusCode(), response.getStatusMessage());
				}
			}
		});
		return wrapper.getErrorType() == GDataWrapper.ERROR_NO_ERROR;
	}

}
