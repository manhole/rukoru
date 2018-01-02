package hoshisugi.rukoru.app.models;

import com.amazonaws.services.s3.model.S3ObjectSummary;

import hoshisugi.rukoru.flamework.util.AssetUtil;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;

public class S3Object extends S3Item {

	public S3Object(final S3ObjectSummary object) {
		setBucketName(object.getBucketName());
		setKey(object.getKey());
		setName(object.getKey());
		setLastModified(object.getLastModified());
		setSize(object.getSize());
		setStorageClass(object.getStorageClass());
		setOwner(object.getOwner().getDisplayName());
	}

	@Override
	public Image getIcon() {
		return AssetUtil.getImage("16x16/document.png");
	}

	@Override
	public TreeItem<S3Item> createTreeItem() {
		return null;
	}

	@Override
	public boolean isContainer() {
		return false;
	}

}