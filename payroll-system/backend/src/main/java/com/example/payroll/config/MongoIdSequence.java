package com.example.payroll.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Component
@RequiredArgsConstructor
class MongoIdSequence {
    private final MongoTemplate mongo;

    long next(String name) {
        Counter counter = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(name)),
                new Update().inc("seq", 1),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                Counter.class);
        if (counter == null) throw new IllegalStateException("Could not allocate id for " + name);
        return counter.getSeq();
    }

    @Document(collection = "counters")
    @Getter @Setter
    static class Counter {
        @Id private String id;
        private long seq;
    }
}

@Component
@RequiredArgsConstructor
class LongIdAssigner extends AbstractMongoEventListener<Object> {
    private final MongoIdSequence sequences;

    @Override
    public void onBeforeConvert(BeforeConvertEvent<Object> event) {
        Object source = event.getSource();
        Field id = findId(source.getClass());
        if (id == null || id.getType() != Long.class) return;
        try {
            id.setAccessible(true);
            if (id.get(source) == null) id.set(source, sequences.next(source.getClass().getSimpleName()));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field findId(Class<?> type) {
        while (type != null && type != Object.class) {
            try { return type.getDeclaredField("id"); }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        return null;
    }
}
