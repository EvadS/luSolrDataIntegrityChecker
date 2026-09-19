package ua.lz.ep.utils;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class EditionUtils {


    private EditionUtils() {}


    public  static List<String> buildEditionIds(String id, List<String> editionList) {
        List<String> editionIds = new ArrayList<>();
        for (String edition : editionList) {
            if (StringUtils.isEmpty(edition)) {
                editionIds.add(id);
            } else {
                editionIds.add(String.format("%s_%s", id, edition)); // В данном случае просто добавляем строку как есть
            }
        }
        return editionIds;
    }
}