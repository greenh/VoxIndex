package voxfront.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;

public interface Resources extends ClientBundle {
	
	public static final Resources R =  GWT.create(Resources.class);

	@Source("resources/blank.png") ImageResource blank();
	@Source("resources/blue.png") ImageResource blue();
	@Source("resources/check_green_24.png") ImageResource check_green_24();
	@Source("resources/check_green.png") ImageResource check_green();
	@Source("resources/down_blue.png") ImageResource down_blue();
	@Source("resources/green_dot.png") ImageResource green_dot();
	@Source("resources/green.png") ImageResource green();
	@Source("resources/launcher.png") ImageResource launcher();
	@Source("resources/red_dot.png") ImageResource red_dot();
	@Source("resources/red.png") ImageResource red();
	@Source("resources/white_dot.png") ImageResource white_dot();
	@Source("resources/x_red.png") ImageResource x_red();
	@Source("resources/yellow_dot.png") ImageResource yellow_dot();
	@Source("resources/yellow.png") ImageResource yellow();
}
