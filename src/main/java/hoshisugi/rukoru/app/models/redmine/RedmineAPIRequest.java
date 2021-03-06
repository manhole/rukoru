package hoshisugi.rukoru.app.models.redmine;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

public abstract class RedmineAPIRequest {

	private static final String URI = "http://redmine.dataspidercloud.tokyo";

	private final String path;
	private Integer limit;
	private Integer offset;

	public RedmineAPIRequest(final String path) {
		this.path = path;
	}

	public Integer getLimit() {
		return limit;
	}

	public void setLimit(final Integer limit) {
		this.limit = limit;
	}

	public Integer getOffset() {
		return offset;
	}

	public void setOffset(final Integer offset) {
		this.offset = offset;
	}

	public void addOffset(final int offset) {
		if (this.offset == null) {
			this.offset = offset;
		} else {
			this.offset += offset;
		}
	}

	public String getPath() {
		return path;
	}

	protected WebTarget applyQueries(final WebTarget target) {
		WebTarget t = target;
		if (limit != null) {
			t = t.queryParam("limit", limit);
		}
		if (offset != null) {
			t = t.queryParam("offset", offset);
		}
		return t;
	}

	public WebTarget createTarget() {
		final Client client = ClientBuilder.newClient();
		return applyQueries(client.target(URI).path(path));
	}
}
