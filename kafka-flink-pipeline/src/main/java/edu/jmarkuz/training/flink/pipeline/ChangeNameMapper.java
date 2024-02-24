package edu.jmarkuz.training.flink.pipeline;

import edu.jmarkuz.training.model.WikimediaData;
import org.apache.flink.api.common.functions.MapFunction;

public class ChangeNameMapper implements MapFunction<WikimediaData, WikimediaData> {
    @Override
    public WikimediaData map(WikimediaData data) {

        String userName = data.getData().getUser();
        if (data.getData().isBot()) {
            String botNameInUpper = userName.toUpperCase().concat("-BOT");
            data.getData().setUser(userName.concat(botNameInUpper));
        } else {
            String userNameInUpper = userName.toUpperCase().concat("-USED");
            data.getData().setUser(userName.concat(userNameInUpper));
        }
        return data;
    }
}
