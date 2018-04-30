package hoshisugi.rukoru.app.services.ds;

import static org.glassfish.jersey.client.ClientProperties.CONNECT_TIMEOUT;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.apache.http.HttpStatus;
import org.glassfish.jersey.client.ClientConfig;

import hoshisugi.rukoru.app.models.ds.DSLogWriter;
import hoshisugi.rukoru.app.models.ds.DSSetting;
import hoshisugi.rukoru.framework.base.BaseService;
import hoshisugi.rukoru.framework.cli.CLI;
import hoshisugi.rukoru.framework.cli.CLIState;
import hoshisugi.rukoru.framework.util.ConcurrentUtil;
import hoshisugi.rukoru.framework.util.IOUtil;

public class DSServiceImpl extends BaseService implements DSService {

	@Override
	public void startServerExe(final DSSetting dsSetting, final DSLogWriter writer, final Consumer<CLIState> callback)
			throws IOException {
		startServer(dsSetting, writer, callback);
	}

	@Override
	public void stopServerExe(final DSSetting dsSetting, final Consumer<CLIState> callback) throws IOException {
		if (!isServerRunning(dsSetting)) {
			callback.accept(null);
			return;
		}
		CLI.command("Shutdown.exe").directory(dsSetting.getPath("server/bin/"))
				.successCondition(s -> s.contains("停止しました。")).callback(callback).execute();
	}

	@Override
	public void startStudioExe(final DSSetting dsSetting, final DSLogWriter writer, final Consumer<CLIState> callback)
			throws IOException {
		startStudio(dsSetting, writer, callback);
	}

	@Override
	public void stopStudioExe(final DSSetting dsSetting, final Consumer<CLIState> callback) throws IOException {
		if (!isStudioRunning(dsSetting)) {
			callback.accept(null);
			return;
		}
		final Optional<WindowsProcess> process = getStudioExeProcess(dsSetting);
		if (process.isPresent()) {
			CLI.command("taskkill").options("/pid", process.get().getPid(), "/t").callback(callback).execute();
		} else {
			callback.accept(null);
		}
	}

	@Override
	public void startServerService(final DSSetting dsSetting, final DSLogWriter writer,
			final Consumer<CLIState> callback) throws IOException {
		if (!dsSetting.getServiceName().isPresent()) {
			writer.writeLine("サービスは登録されていません。");
			writer.shutDown();
			callback.accept(null);
			return;
		}
		final CLIState cliState = CLI.command("sc").options("start", dsSetting.getServiceName().get())
				.callback(callback).execute();
		try (BufferedReader br = IOUtil.newBufferedReader(cliState.getInputStream())) {
			for (String line = null; (line = br.readLine()) != null;) {
				writer.writeLine(line);
			}
		} finally {
			writer.shutDown();
		}
	}

	@Override
	public void stopServerService(final DSSetting dsSetting, final Consumer<CLIState> callback) throws IOException {
		if (!dsSetting.getServiceName().isPresent()) {
			callback.accept(null);
			return;
		}
		CLI.command("sc").options("stop", dsSetting.getServiceName().get()).successCondition(s -> s.contains("STOPPED"))
				.callback(callback).execute();
	}

	@Override
	public void startServerBat(final DSSetting dsSetting, final DSLogWriter writer, final Consumer<CLIState> callback)
			throws IOException {
		startServer(dsSetting, writer, callback);
	}

	@Override
	public void stopServerBat(final DSSetting dsSetting, final Consumer<CLIState> callback) throws IOException {
		if (!isServerRunning(dsSetting)) {
			callback.accept(null);
			return;
		}
		final Optional<Netstat> netstat = getServerBatProcess(dsSetting);
		if (netstat.isPresent()) {
			CLI.command("taskkill").options("/pid", netstat.get().getPid(), "/f").callback(callback).execute();
		} else {
			callback.accept(null);
		}
	}

	@Override
	public void startStudioBat(final DSSetting dsSetting, final DSLogWriter writer, final Consumer<CLIState> callback)
			throws IOException {
		startStudio(dsSetting, writer, callback);
	}

	@Override
	public void stopStudioBat(final DSSetting dsSetting, final Consumer<CLIState> callback) throws IOException {
		if (!isStudioRunning(dsSetting)) {
			callback.accept(null);
			return;
		}
		final Optional<String> pid = getStudioBatProcess(dsSetting);
		if (pid.isPresent()) {
			CLI.command("taskkill").options("/pid", pid.get(), "/t").callback(callback).execute();
		} else {
			callback.accept(null);
		}
	}

	@Override
	public void changePort(final DSSetting setting, final String port) throws IOException {
		final Path serverPropPath = setting.getPath("server/system/conf/webcontainer.properties");
		try (BufferedWriter writer = Files.newBufferedWriter(serverPropPath, StandardOpenOption.CREATE)) {
			writer.write("port=" + port);
		}
		final Path studioPropPath = setting.getPath("client/conf/boot.properties");
		if (Files.exists(studioPropPath)) {
			final Properties studioProp = new Properties();
			try (InputStream input = Files.newInputStream(studioPropPath)) {
				studioProp.load(input);
			}
			studioProp.setProperty("server.port", port);
			try (OutputStream output = Files.newOutputStream(studioPropPath)) {
				studioProp.store(output, "");
			}
		}
	}

	@Override
	public boolean isServerRunning(final DSSetting dsSetting) {
		try {
			final ClientConfig config = new ClientConfig().property(CONNECT_TIMEOUT, 500);
			final Client client = ClientBuilder.newClient(config);
			final WebTarget target = client.target(dsSetting.getServerUrl());
			final Response response = target.request().get();
			return response.getStatus() == HttpStatus.SC_OK;
		} catch (final Exception e) {
			return false;
		}
	}

	@Override
	public boolean isStudioRunning(final DSSetting dsSetting) {
		return Files.exists(dsSetting.getPath("client/bin/.lock"));
	}

	private Optional<Netstat> getServerBatProcess(final DSSetting dsSetting) throws IOException {
		final CLIState netstatState = CLI.command("netstat")
				.options("-aon", "|", "find", String.format("\"0.0.0.0:%s\"", dsSetting.getPort())).execute();
		try (BufferedReader reader = IOUtil.newBufferedReader(netstatState.getInputStream())) {
			for (String line = null; (line = reader.readLine()) != null;) {
				if (line.isEmpty()) {
					continue;
				}
				return Optional.of(new Netstat(line));
			}
		}
		return Optional.empty();
	}

	private Optional<String> getStudioBatProcess(final DSSetting dsSetting) throws IOException {
		final CLIState jps = CLI.command("jps").options("-v", "|", "find", "\"DataSpiderStudioMain\"").execute();
		final List<String> pids = new ArrayList<>();
		try (BufferedReader reader = IOUtil.newBufferedReader(jps.getInputStream())) {
			for (String line = null; (line = reader.readLine()) != null;) {
				if (line.isEmpty()) {
					continue;
				}
				final String pid = line.split(" ")[0];
				pids.add(pid);
			}
		}

		for (final String pid : pids) {
			final CLIState jinfo = CLI.command("jinfo").options(pid, "|", "find", "\"user.dir\"").execute();
			try (BufferedReader reader = IOUtil.newBufferedReader(jinfo.getInputStream())) {
				for (String line = null; (line = reader.readLine()) != null;) {
					if (line.contains(dsSetting.getExecutionPath())) {
						return Optional.of(pid);
					}
				}
			}
		}
		return Optional.empty();
	}

	private Optional<WindowsProcess> getStudioExeProcess(final DSSetting dsSetting) throws IOException {
		final CLIState wmicState = CLI.command("WMIC")
				.options("PROCESS", "WHERE", "\"Name LIKE '" + dsSetting.getStudioExecutorName() + "'\"", "GET",
						"ExecutablePath,Name,ProcessId", "/FORMAT:CSV")
				.execute();
		try (BufferedReader reader = IOUtil.newBufferedReader(wmicState.getInputStream())) {
			for (String line = null; (line = reader.readLine()) != null;) {
				if (line.isEmpty()) {
					continue;
				}
				final WindowsProcess process = new WindowsProcess(line);
				if (process.executablePath.startsWith(dsSetting.getExecutionPath())) {
					return Optional.of(process);
				}
			}
		}
		return Optional.empty();
	}

	private void startServer(final DSSetting dsSetting, final DSLogWriter writer, final Consumer<CLIState> callback)
			throws IOException {
		if (isServerRunning(dsSetting)) {
			writer.writeLine("Server はすでに起動しています。");
			writer.shutDown();
			callback.accept(null);
			return;
		}
		final CLIState cliState = CLI.command(dsSetting.getServerExecutorName())
				.directory(dsSetting.getPath("server/bin")).successCondition(s -> s.contains("正常に起動しました。"))
				.failureCondition(s -> s.contains("起動に失敗しました。")).callback(callback).execute();
		try (final BufferedReader br = IOUtil.newBufferedReader(cliState.getInputStream())) {
			for (String line = null; (line = br.readLine()) != null;) {
				writer.writeLine(line);
			}
			writer.shutDown();
		}
	}

	private void startStudio(final DSSetting dsSetting, final DSLogWriter writer, final Consumer<CLIState> callback)
			throws IOException {
		if (isStudioRunning(dsSetting)) {
			writer.writeLine("Studio はすでに起動しています。");
			writer.shutDown();
			callback.accept(null);
			return;
		}
		final CLIState cliState = CLI.command(dsSetting.getStudioExecutorName())
				.directory(dsSetting.getPath("client/bin")).callback(callback).execute();
		ConcurrentUtil.run(() -> {
			try (final BufferedReader br = IOUtil.newBufferedReader(cliState.getInputStream())) {
				for (String line = null; (line = br.readLine()) != null;) {
					writer.writeLine(line);
				}
				writer.shutDown();
			}
		});
		cliState.succeed();
		callback.accept(cliState);
	}

	static class WindowsProcess {
		private final String node;
		private final String executablePath;
		private final String name;
		private final String pid;

		public WindowsProcess(final String line) {
			final String[] cols = line.split(",");
			node = cols[0];
			executablePath = cols[1];
			name = cols[2];
			pid = cols[3];
		}

		public String getNode() {
			return node;
		}

		public String getExecutablePath() {
			return executablePath;
		}

		public String getName() {
			return name;
		}

		public String getPid() {
			return pid;
		}
	}

	static class Netstat {
		private final String protocol;
		private final String localAddress;
		private final String foreignAddress;
		private final String state;
		private final String pid;

		public Netstat(final String line) {
			final String[] cols = line.trim().split("\\s+");
			protocol = cols[0];
			localAddress = cols[1];
			foreignAddress = cols[2];
			state = cols[3];
			pid = cols[4];
		}

		public String getProtocol() {
			return protocol;
		}

		public String getLocalAddress() {
			return localAddress;
		}

		public String getForeignAddress() {
			return foreignAddress;
		}

		public String getState() {
			return state;
		}

		public String getPid() {
			return pid;
		}

	}
}
