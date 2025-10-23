package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private static final long MAX_POINT = 1000000L;
    private static final long MIN_AMOUNT = 1L;

    // 동시성 제어: 사용자별 Lock을 관리하는 Map
    private final Map<Long, Lock> userLocks = new ConcurrentHashMap<>();

    //특정 유저의 포인트를 조회하는 기능
    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    // 동시성 제어: 사용자별 Lock 획득
    private Lock getUserLock(long userId) {
        return userLocks.computeIfAbsent(userId, id -> new ReentrantLock());
    }

    public UserPoint getUserPoint(long id) {
        // 사용자 검증
        validateUser(id);
        // 사용자 포인트 정보 조회
        return userPointTable.selectById(id);
    }

    //특정 유저의 포인트 충전/이용 내역을 조회
    public List<PointHistory> getUserHistories(long id) {
        //사용자 검증
        validateUser(id);
        //사용자 모든기록 조회
        return pointHistoryTable.selectAllByUserId(id);
    }

    //특정 유저의 포인트를 충전하는 기능
    public UserPoint chargePoint(long id, long amount) {
        //사용자 검증
        validateUser(id);
        //충전 금액 검증
        validateAmount_Min(amount);

        // 동시성 제어: 사용자별 Lock 획득
        Lock lock = getUserLock(id);
        lock.lock();
        try {
            //사용자 금액 조회
            UserPoint currentPoint = userPointTable.selectById(id);
            //합산 값 계산
            long newPoint = amount + currentPoint.point();
            //합산 값 검증
            validateAmount_Max(newPoint);
            //사용자 금액 update
            UserPoint userPoint = userPointTable.insertOrUpdate(id, newPoint);
            //사용자 기록 insert
            pointHistoryTable.insert(id, amount, TransactionType.CHARGE, userPoint.updateMillis());
            //사용자 포인트 정보 반환
            return userPoint;
        } finally {
            lock.unlock();
        }
    }

    //특정 유저의 포인트를 사용하는 기능
    public UserPoint usePoint(long id, long amount) {
        //사용자 검증
        validateUser(id);
        //사용 금액 검증
        validateAmount_Min(amount);

        // 동시성 제어: 사용자별 Lock 획득
        Lock lock = getUserLock(id);
        lock.lock();
        try {
            //차감 금액 계산
            long balance = userPointTable.selectById(id).point() - amount;
            //잔액 검증
            validateBalance(balance);
            //사용자 포인트 update
            UserPoint userPoint = userPointTable.insertOrUpdate(id, balance);
            //사용자기록 insert
            pointHistoryTable.insert(id, amount, TransactionType.USE, userPoint.updateMillis());
            return userPoint;
        } finally {
            lock.unlock();
        }
    }


    private void validateUser(long id){
        if (id <= 0) {
            throw new IllegalArgumentException("유효하지 않은 사용자 ID 입니다.");
        }
    }

    private void validateAmount_Min(long amount) {
        if (amount < MIN_AMOUNT) {
            throw new IllegalArgumentException("포인트 충전 최소금액은 1 이상이여야 합니다.");
        }
    }

    private void validateBalance(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("포인트 사용 금액이 잔액보다 큽니다.");
        }
    }

    private void validateAmount_Max(long amount){
        if(amount>MAX_POINT){
            throw new IllegalArgumentException("포인트 최대금액" + MAX_POINT + "을 초과했습니다.");
        }
    }
}
