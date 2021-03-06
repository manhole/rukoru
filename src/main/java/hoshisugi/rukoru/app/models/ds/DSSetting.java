package hoshisugi.rukoru.app.models.ds;

import static hoshisugi.rukoru.app.enums.ExecutionType.BAT;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.google.common.util.concurrent.UncheckedExecutionException;

import hoshisugi.rukoru.app.enums.DSSettingOperation;
import hoshisugi.rukoru.app.enums.ExecutionType;
import hoshisugi.rukoru.app.enums.StudioMode;
import hoshisugi.rukoru.app.models.settings.DBEntity;
import hoshisugi.rukoru.framework.util.AssetUtil;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class DSSetting extends DBEntity {
	private final StringProperty name = new SimpleStringProperty(this, "name");
	private final StringProperty executionPath = new SimpleStringProperty(this, "executionPath");
	private final ObjectProperty<ExecutionType> executionType = new SimpleObjectProperty<>(this, "executionType");
	private final ObjectProperty<StudioMode> studioMode = new SimpleObjectProperty<>(this, "studioMode");
	private final StringProperty state = new SimpleStringProperty(this, "state");
	private final Properties dsProp = AssetUtil.loadProperties("ds.properties");

	public DSSetting() {
	}

	public DSSetting(final ResultSet rs) {
		try {
			setId(rs.getInt("id"));
			setName(rs.getString("name"));
			setExecutionPath(rs.getString("executionPath"));
			setExecutionType(ExecutionType.of(rs.getString("executiontype")));
			setStudioMode(StudioMode.of(rs.getString("studiomode")));
			setCreatedAt(rs.getTimestamp("created_at"));
			setUpdatedAt(rs.getTimestamp("updated_at"));
			setState(DSSettingOperation.Update);
		} catch (final SQLException e) {
			throw new UncheckedExecutionException(e);
		}
	}

	public String getName() {
		return name.get();
	}

	public void setName(final String name) {
		this.name.set(name);
	}

	public String getExecutionPath() {
		return executionPath.get();
	}

	public void setExecutionPath(final String dsHome) {
		this.executionPath.set(dsHome);
	}

	public ExecutionType getExecutionType() {
		return executionType.get();
	}

	public void setExecutionType(final ExecutionType executionType) {
		this.executionType.set(executionType);
	}

	public StudioMode getStudioMode() {
		return studioMode.get();
	}

	public void setStudioMode(final StudioMode studioMode) {
		this.studioMode.set(studioMode);
	}

	public String getState() {
		return state.get();
	}

	public void setState(final DSSettingOperation state) {
		this.state.set(state.toString());
	}

	public StringProperty nameProperty() {
		return name;
	}

	public StringProperty executionPathProperty() {
		return executionPath;
	}

	public ObjectProperty<ExecutionType> executionTypeProperty() {
		return executionType;
	}

	public ObjectProperty<StudioMode> studioModeProperty() {
		return studioMode;
	}

	public StringProperty stateProperty() {
		return state;
	}

	public Optional<String> getServiceName() {
		final Properties p = new Properties();
		try (InputStream input = Files
				.newInputStream(Paths.get(getExecutionPath() + "/Uninstall/installvariables.properties"))) {
			p.load(input);
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
		return Optional.ofNullable(p.getProperty("SHORT_SERVICE_NAME"));
	}

	public boolean isServerInstalled() {
		return Files.isExecutable(Paths.get(getExecutionPath()).resolve("server/bin").resolve(getServerExecutorName()));
	}

	public boolean isStudioInstalled() {
		switch (getStudioMode()) {
		case Desktop:
			return Files.isExecutable(getPath("client/bin").resolve(getStudioExecutorName()));
		case Silverlight:
			return Files.exists(getPath(
					"server/system/kernel/modules/webcontainer/META-INF/catalina/webapps/RelayServer/WebStudio.xap"));
		case WPF:
			return Files.exists(getPath(
					"server/system/kernel/modules/webcontainer/META-INF/catalina/webapps/RelayServer/publish/Studio.application"));
		}
		return false;
	}

	public String getServerExecutorName() {
		if (getExecutionType() == BAT) {
			return dsProp.getProperty("ds.server.bat");
		} else {
			return dsProp.getProperty("ds.server.exe");
		}
	}

	public String getStudioExecutorName() {
		if (getExecutionType() == BAT) {
			return dsProp.getProperty("ds.client.bat");
		} else {
			return dsProp.getProperty("ds.client.exe");
		}
	}

	public String getPort() {
		if (Files.exists(getPath("server/conf/dsserver.xml"))) {
			try {
				final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				final DocumentBuilder db = dbf.newDocumentBuilder();
				final Document doc = db.parse(new FileInputStream(getPath("server/conf/dsserver.xml").toFile()));

				final XPathFactory factory = XPathFactory.newInstance();
				final XPath path = factory.newXPath();
				final XPathExpression expression = path
						.compile("//module[@type='webcontainer']/attribute[@key='port']");
				final Node node = (Node) expression.evaluate(doc, XPathConstants.NODE);
				return node.getTextContent();
			} catch (final IOException ioe) {
				throw new UncheckedIOException(ioe);
			} catch (final ParserConfigurationException | SAXException | XPathExpressionException e) {
				throw new UncheckedExecutionException(e);
			}

		} else if (Files.exists(getPath("server/system/conf/webcontainer.properties"))) {
			final Properties p = new Properties();
			try (InputStream input = Files.newInputStream(
					Paths.get(getExecutionPath()).resolve("server/system/conf/webcontainer.properties"))) {
				p.load(input);
			} catch (final IOException e) {
				throw new UncheckedIOException(e);
			}
			return p.getProperty("port");
		}
		return null;
	}

	public Path getPath(final String path) {
		return Paths.get(getExecutionPath()).resolve(path);
	}

	public String getServerUrl() {
		return "http://localhost:" + getPort();
	}

	public String getStudioForWebUrl() {
		return getServerUrl() + "/WebStudio/";
	}

	public String getWPFUrl() {
		return getStudioForWebUrl() + "publish/Studio.application";
	}

	public String getServerId() {
		return String.format("server(%s)", getId());
	}

	public String getStudioId() {
		return String.format("studio(%s)", getId());
	}
}
