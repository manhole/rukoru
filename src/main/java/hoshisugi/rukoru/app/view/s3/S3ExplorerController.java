package hoshisugi.rukoru.app.view.s3;

import java.net.URL;
import java.util.ResourceBundle;

import com.google.inject.Inject;

import hoshisugi.rukoru.app.models.auth.AuthSetting;
import hoshisugi.rukoru.app.models.s3.ExplorerSelection;
import hoshisugi.rukoru.app.models.s3.S3Item;
import hoshisugi.rukoru.app.models.s3.S3Root;
import hoshisugi.rukoru.app.services.s3.S3Service;
import hoshisugi.rukoru.framework.base.BaseController;
import hoshisugi.rukoru.framework.util.ConcurrentUtil;
import javafx.fxml.FXML;
import javafx.scene.control.SplitPane;

public class S3ExplorerController extends BaseController {

	@FXML
	private SplitPane splitPane;

	@Inject
	private S3Service s3Service;

	private final ExplorerSelection selection = new ExplorerSelection(20);

	private final S3Root rootItem = new S3Root();

	@Override
	public void initialize(final URL url, final ResourceBundle resource) {
		selection.select(rootItem);
		reload(rootItem);
	}

	public ExplorerSelection getSelection() {
		return selection;
	}

	public S3Root getRootItem() {
		return rootItem;
	}

	public void reload(final S3Item item) {
		ConcurrentUtil.run(() -> {
			if (AuthSetting.hasSetting()) {
				s3Service.updateItems(item);
			}
		});
	}
}