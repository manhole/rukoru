package hoshisugi.rukoru.app.models.ds;

import java.io.IOException;
import java.util.function.Consumer;

import hoshisugi.rukoru.framework.cli.CLIState;

public class DSExeManager extends DSManagerBase {

	public DSExeManager(final DSEntry entry) {
		super(entry);
	}

	@Override
	public void startServer(final DSSetting dsSetting, final DSLogWriter writer, final Consumer<CLIState> callback)
			throws IOException {
		service.startServerExe(dsSetting, writer, callback);
	}

	@Override
	public void stopServer(final DSSetting dsSetting, final Consumer<CLIState> callback) throws IOException {
		service.stopServerExe(dsSetting, callback);
	}

	@Override
	public void startStudio(final DSSetting dsSetting, final DSLogWriter writer, final Consumer<CLIState> callback)
			throws IOException {
		service.startStudioExe(dsSetting, writer, callback);
	}

	@Override
	public void stopStudio(final DSSetting dsSetting, final Consumer<CLIState> callback) throws IOException {
		service.stopStudioExe(dsSetting, callback);
	}

}
