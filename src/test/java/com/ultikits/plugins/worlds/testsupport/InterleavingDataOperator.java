package com.ultikits.plugins.worlds.testsupport;

import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test support: a server's own operator that lets "another server" write first. Before each whole-row
 * write this server makes -- {@code updateIf}, {@code updateCounted} or {@code update(T)} -- it runs
 * {@code otherServer} up to {@code interleavings} times, so the other server's write lands between this
 * server's read and its write, the window a conditional write exists for (UltiKits/UltiWorlds#55).
 * Everything else is forwarded unchanged to the real operator.
 */
public final class InterleavingDataOperator<T extends BaseDataEntity<String>> implements DataOperator<T> {

    private final DataOperator<T> real;
    private final Runnable otherServer;
    private final AtomicInteger interleavingsLeft;
    private final AtomicInteger rowWrites = new AtomicInteger();

    public InterleavingDataOperator(DataOperator<T> real, Runnable otherServer, int interleavings) {
        this.real = real;
        this.otherServer = otherServer;
        this.interleavingsLeft = new AtomicInteger(interleavings);
    }

    /** How many whole-row writes this server attempted. */
    public int rowWrites() {
        return rowWrites.get();
    }

    private void beforeRowWrite() {
        rowWrites.incrementAndGet();
        if (interleavingsLeft.getAndDecrement() > 0) {
            otherServer.run();
        }
    }

    @Override
    public boolean updateIf(T entity, WhereCondition... expected) {
        beforeRowWrite();
        return real.updateIf(entity, expected);
    }

    @Override
    public int updateCounted(T entity) {
        beforeRowWrite();
        return real.updateCounted(entity);
    }

    @Override
    public void update(T obj) throws IllegalAccessException {
        beforeRowWrite();
        real.update(obj);
    }

    @Override
    public boolean exist(T object) {
        return real.exist(object);
    }

    @Override
    public boolean exist(WhereCondition... whereConditions) {
        return real.exist(whereConditions);
    }

    @Override
    public T getById(Object id) {
        return real.getById(id);
    }

    @Override
    public List<T> getAll() {
        return real.getAll();
    }

    @Override
    public List<T> getAll(WhereCondition... whereConditions) {
        return real.getAll(whereConditions);
    }

    @Override
    public List<T> getLike(String column, String value, LikeType likeType) {
        return real.getLike(column, value, likeType);
    }

    @Override
    public List<T> page(int page, int size, WhereCondition... whereConditions) {
        return real.page(page, size, whereConditions);
    }

    @Override
    public void insert(T obj) {
        real.insert(obj);
    }

    @Override
    public void del(WhereCondition... whereConditions) {
        real.del(whereConditions);
    }

    @Override
    public void delById(Object id) {
        real.delById(id);
    }

    @Override
    public void update(String column, Object value, Object id) {
        real.update(column, value, id);
    }

    @Override
    public Query<T> query() {
        return real.query();
    }

    @Override
    public <R> R transaction(Callable<R> action) throws Exception {
        return real.transaction(action);
    }

    @Override
    public void transaction(Runnable action) {
        real.transaction(action);
    }

    @Override
    public void insertAll(List<T> entities) {
        real.insertAll(entities);
    }

    @Override
    public void updateAll(List<T> entities) throws IllegalAccessException {
        real.updateAll(entities);
    }
}
