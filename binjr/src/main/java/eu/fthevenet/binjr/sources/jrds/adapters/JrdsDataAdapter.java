package eu.fthevenet.binjr.sources.jrds.adapters;

import com.google.gson.Gson;
import eu.fthevenet.binjr.data.adapters.DataAdapter;
import eu.fthevenet.binjr.data.adapters.DataAdapterException;
import eu.fthevenet.binjr.data.adapters.DataAdapterInfo;
import eu.fthevenet.binjr.data.adapters.TimeSeriesBinding;
import eu.fthevenet.binjr.data.parsers.CsvParser;
import eu.fthevenet.binjr.data.parsers.DataParser;
import eu.fthevenet.binjr.data.timeseries.DoubleTimeSeries;
import eu.fthevenet.binjr.dialogs.Dialogs;
import eu.fthevenet.binjr.logging.Profiler;
import eu.fthevenet.binjr.xml.XmlUtils;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TreeItem;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.bind.JAXB;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class provides an implementation of {@link DataAdapter} for JRDS.
 *
 * @author Frederic Thevenet
 */
@DataAdapterInfo(name = "JRDS", description = "A binjr data adapter for JRDS.")
@XmlRootElement(name = "JrdsDataAdapter")
@XmlAccessorType(XmlAccessType.FIELD)
public class JrdsDataAdapter implements DataAdapter<Double> {
   @XmlTransient
    private static final Logger logger = LogManager.getLogger(JrdsDataAdapter.class);
    @XmlTransient
    private static final String SEPARATOR = ",";

    private final String jrdsHost;
    private final int jrdsPort;
    private final String jrdsPath;
    private final String jrdsProtocol;
    private final ZoneId zoneId;
    private final String encoding;
    private final JrdsTreeFilter treeFilter;



    /**
     * Builds a new instance of the {@link JrdsDataAdapter} class from the provided parameters.
     *
     * @param url    the URL to the JRDS webapp.
     * @param zoneId the id of the time zone used to record dates.
     * @return a new instance of the {@link JrdsDataAdapter} class.
     */
    public static JrdsDataAdapter fromUrl(String url, ZoneId zoneId, JrdsTreeFilter treeFilter) throws MalformedURLException {
        URL u = new URL(url.replaceAll("/$", ""));
        return new JrdsDataAdapter(u.getProtocol(), u.getHost(), u.getPort(), u.getPath(), zoneId, "utf-8", treeFilter);
    }

    private JrdsDataAdapter() {
        jrdsHost = "";
        jrdsPort = 0;
        jrdsPath = "";
        jrdsProtocol = "";
        zoneId = null;
        encoding = "";
        treeFilter = null;
    }

    /**
     * Initializes a new instance of the {@link JrdsDataAdapter} class.
     *
     * @param jrdsProtocol the URL scheme if the JRDS webapp.
     * @param hostname     the host of the JRDS webapp.
     * @param port         the port of the JRDS webapp.
     * @param path         the url path of the JRDS webapp.
     * @param zoneId       the id of the time zone used to record dates.
     * @param encoding     the encoding used by the download servlet.
     * @param treeFilter
     */
    public JrdsDataAdapter(String jrdsProtocol, String hostname, int port, String path, ZoneId zoneId, String encoding, JrdsTreeFilter treeFilter) {
        this.jrdsHost = hostname;
        this.jrdsPort = port;
        this.jrdsPath = path;
        this.jrdsProtocol = jrdsProtocol;
        this.zoneId = zoneId;
        this.encoding = encoding;
        this.treeFilter = treeFilter;
    }

    //region [DataAdapter Members]
    @Override
    public TreeItem<TimeSeriesBinding<Double>> getBindingTree() throws DataAdapterException {
        Gson gson = new Gson();
        JsonTree t = gson.fromJson(getJsonTree(treeFilter), JsonTree.class);
        Map<String, JsonTree.JsonItem> m = Arrays.stream(t.items).collect(Collectors.toMap(o -> o.id, (o -> o)));
        TreeItem<TimeSeriesBinding<Double>> tree = new TreeItem<>(new JrdsSeriesBinding(getSourceName(), "/", this));
        List<TreeItem<JsonTree.JsonItem>> l = new ArrayList<>();
        for (JsonTree.JsonItem branch : Arrays.stream(t.items).filter(jsonItem -> "tree".equals(jsonItem.type)).collect(Collectors.toList())) {
            attachNode(tree, branch.id, m);
        }
        return tree;
    }

    @Override
    public long getData(String path, Instant begin, Instant end, OutputStream out) throws DataAdapterException {
        URIBuilder requestUrl = new URIBuilder()
                .setScheme(jrdsProtocol)
                .setHost(jrdsHost)
                .setPort(jrdsPort)
                .setPath(jrdsPath + "/download")
                .addParameter("id", path)
                .addParameter("begin", Long.toString(begin.toEpochMilli()))
                .addParameter("end", Long.toString(end.toEpochMilli()));

        return doHttpGet(requestUrl, response -> {
            int status = response.getStatusLine().getStatusCode();
            if (status >= 200 && status < 300) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    long length = entity.getContentLength();
                    entity.writeTo(out);
                    return length;
                }
                return 0L;
            }
            else {
                throw new ClientProtocolException("Unexpected response status: " + status + " - " + response.getStatusLine().getReasonPhrase());
            }
        });
    }

    @Override
    public String getSourceName() {
        return "[JRDS] " + jrdsHost + ":" + jrdsPort + " (" + zoneId.toString() + ")";
    }

    @Override
    public String getEncoding() {
        return encoding;
    }

    @Override
    public ZoneId getTimeZoneId() {
        return zoneId;
    }

    @Override
    public DataParser<Double> getParser() {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(getTimeZoneId());
        return new CsvParser<>(getEncoding(), SEPARATOR,
                DoubleTimeSeries::new,
                s -> {
                    Double val = Double.parseDouble(s);
                    return val.isNaN() ? 0 : val;
                },
                s -> ZonedDateTime.parse(s, formatter));
    }
    //endregion

    private TreeItem<TimeSeriesBinding<Double>> attachNode(TreeItem<TimeSeriesBinding<Double>> tree, String id, Map<String, JsonTree.JsonItem> nodes) throws DataAdapterException {
        JsonTree.JsonItem n = nodes.get(id);
        String currentPath = normalizeId(n.id);
        TreeItem<TimeSeriesBinding<Double>> newBranch = new TreeItem<>(new JrdsSeriesBinding(n.name, currentPath, this));
        if (n.children != null) {
            for (JsonTree.JsonItem.JsonTreeRef ref : n.children) {
                attachNode(newBranch, ref._reference, nodes);
            }
        }
        else {
            // add a dummy node so that the branch can be expanded
            newBranch.getChildren().add(new TreeItem<>(null));
            // add a listener so that bindings for individual datastore are added lazily to avoid
            // dozens of individual call to getColumnDataStores when the tree is built.
            newBranch.expandedProperty().addListener(new ChangeListener<Boolean>() {
                @Override
                public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                    if (newValue) {
                        try {
                            Graphdesc graphdesc =  getGraphDescriptor(currentPath);
                            for (int i = 0; i <graphdesc.seriesDescList.size() ; i++) {
                                if (!"none".equals(graphdesc.seriesDescList.get(i).graphType)) {
                                    newBranch.getChildren().add(new TreeItem<>(new JrdsSeriesBinding(graphdesc, i, currentPath, JrdsDataAdapter.this)));
                                }
                            }
                            //remove dummy node
                            newBranch.getChildren().remove(0);
                            // remove the listener so it isn't executed next time node is expanded
                            newBranch.expandedProperty().removeListener(this);
                        } catch (Exception e) {
                            Dialogs.displayException("Failed to retrieve graph description", e);
                        }
                    }
                }
            });
        }
        tree.getChildren().add(newBranch);
        return tree;
    }

    private String normalizeId(String id) {
        if (id == null || id.trim().length() == 0) {
            throw new IllegalArgumentException("Argument id cannot be null or blank");
        }
        String[] data = id.split("\\.");
        return data[data.length - 1];
    }

    private String getJsonTree(JrdsTreeFilter filter) throws DataAdapterException {
        URIBuilder requestUrl = new URIBuilder()
                .setScheme(jrdsProtocol)
                .setHost(jrdsHost)
                .setPort(jrdsPort)
                .setPath(jrdsPath + "/jsontree")
                .addParameter("tab", filter.getCommand());
        return doHttpGet(requestUrl, response -> {
            int status = response.getStatusLine().getStatusCode();
            if (status >= 200 && status < 300) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    return EntityUtils.toString(entity);
                }
                return null;
            }
            else {
                throw new ClientProtocolException("Unexpected response status: " + status + " - " + response.getStatusLine().getReasonPhrase());
            }
        });
    }

    public Graphdesc getGraphDescriptor(String id) throws DataAdapterException {
        URIBuilder requestUrl = new URIBuilder()
                .setScheme(jrdsProtocol)
                .setHost(jrdsHost)
                .setPort(jrdsPort)
                .setPath(jrdsPath + "/graphdesc")
                .addParameter("id", id);

        return doHttpGet(requestUrl, response -> {
            int status = response.getStatusLine().getStatusCode();
            if (status == 404) {
                // This is probably an older version of JRDS that doesn't provide the graphdesc service,
                // so we're falling back to recovering the datastore name from the csv file provided by
                // the download service.
                logger.warn("Cannot found graphdesc service; falling back to legacy mode.");
                try {
                    return getGraphDescriptorLegacy(id);
                } catch (Exception e) {
                    throw new IOException("", e);
                }
            }
            if (status >= 200 && status < 300) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    try {
                        return JAXB.unmarshal(XmlUtils.toNonValidatingSAXSource(entity.getContent()), Graphdesc.class);
                    } catch (Exception e) {
                        throw new IOException("", e);
                    }
                }
                throw new IOException("Http entity in response to [" + requestUrl.toString() + "] is null");
            }
            else {
                throw new ClientProtocolException("Unexpected response status: " + status + " - " + response.getStatusLine().getReasonPhrase());
            }
        });
    }

    private Graphdesc getGraphDescriptorLegacy(String id) throws DataAdapterException {
        Instant now = ZonedDateTime.now().toInstant();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            getData(id, now.minusSeconds(120), now, out);
            try (InputStream in = new ByteArrayInputStream(out.toByteArray())) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(in, encoding))) {
                    String header = br.readLine();
                    if (header == null || header.isEmpty()) {
                        throw new IOException("CSV File is empty!");
                    }
                    String[] headers = header.split(SEPARATOR);
                    if (headers.length < 1){
                        throw new DataAdapterException("Could not to retrieve data store names for graph id=" + id + ": header line in csv is blank.");
                    }
                    Graphdesc desc = new Graphdesc();
                    desc.seriesDescList = new ArrayList<>();
                    for (int i = 1; i < headers.length; i++) {
                        Graphdesc.SeriesDesc d = new Graphdesc.SeriesDesc();
                        d.name = headers[i];
                        desc.seriesDescList.add(d);
                    }
                    return desc;
                }
            }
        } catch (IOException e) {
            throw new DataAdapterException(e);
        }
    }

    private long getProbeData(String targetHost, String probe, Instant begin, Instant end, OutputStream out) throws DataAdapterException {
        URIBuilder requestUrl = new URIBuilder()
                .setScheme(jrdsProtocol)
                .setHost(jrdsHost)
                .setPort(jrdsPort)
                .setPath(jrdsPath + "/download/probe/" + targetHost + "/" + probe)
                .addParameter("begin", Long.toString(begin.toEpochMilli()))
                .addParameter("end", Long.toString(end.toEpochMilli()));

        return doHttpGet(requestUrl, response -> {
            int status = response.getStatusLine().getStatusCode();
            if (status >= 200 && status < 300) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    long length = entity.getContentLength();
                    entity.writeTo(out);
                    return length;
                }
                return 0L;
            }
            else {
                throw new ClientProtocolException("Unexpected response status: " + status + " - " + response.getStatusLine().getReasonPhrase());
            }
        });
    }

    private <T> T doHttpGet(URIBuilder requestUrl, ResponseHandler<T> responseHandler) throws DataAdapterException {
        try (Profiler p = Profiler.start("Executing HTTP request: [" + requestUrl.toString() + "]", logger::trace)) {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                logger.debug(() -> "requestUrl = " + requestUrl);
                HttpGet httpget = new HttpGet(requestUrl.build());
                return httpClient.execute(httpget, responseHandler);
            }
        } catch (IOException e) {
            throw new DataAdapterException("Error executing HTTP request [" + requestUrl.toString() + "]", e);
        } catch (URISyntaxException e) {
            throw new DataAdapterException("Error building URI for request");
        }
    }

    /**
     * POJO definition used to parse JSON message.
     */
    private static class JsonTree {
        String identifier;
        String label;
        JsonItem[] items;

        static class JsonItem {
            String name;
            String id;
            String type;
            JsonTreeRef[] children;

            static class JsonTreeRef {
                String _reference;
            }
        }
    }
}
