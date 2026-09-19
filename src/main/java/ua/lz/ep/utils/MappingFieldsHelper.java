package ua.lz.ep.utils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MappingFieldsHelper {
    private MappingFieldsHelper(){

    }

    public static List<String> objectToStringList(Object listObj) {
        if (listObj instanceof Collection) {
            return ((Collection<?>) listObj).stream()
                    .map(Objects::toString)
                    .collect(Collectors.toList());
        } else if (listObj != null) {
            return Collections.singletonList(listObj.toString());
        } else {
            return Collections.emptyList();
        }
    }
}
