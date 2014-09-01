package fbutils;

import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient.AccessToken;

public class LoggedInFacebookClient extends DefaultFacebookClient {

	public LoggedInFacebookClient() {
		AccessToken accessToken = this.obtainAppAccessToken(play.Play.application().configuration().getString("fb.appId"),  play.Play.application().configuration().getString("fb.appSecret"));
        this.accessToken = accessToken.getAccessToken();
    }
	
}
