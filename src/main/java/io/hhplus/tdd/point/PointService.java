package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    //특정 유저의 포인트를 조회하는 기능
    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
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
        //사용자 금액 조회
        UserPoint currentPoint = userPointTable.selectById(id);
        //포인트 최대값 검증
        long newPoint = amount+currentPoint.point();
        //사용자 금액 update
        UserPoint userPoint = userPointTable.insertOrUpdate(id,newPoint);
        //사용자 기록 insert
        pointHistoryTable.insert(id, amount, TransactionType.CHARGE, userPoint.updateMillis());
        //사용자 포인트 정보 반환
        return userPoint;
    }

    //특정 유저의 포인트를 사용하는 기능
    public UserPoint usePoint(long id, long amount) {
        //사용자 검증
        validateUser(id);
        //차감 금액 계산
        long balance = userPointTable.selectById(id).point() - amount;
        //사용자 포인트 update
        UserPoint userPoint = userPointTable.insertOrUpdate(id,balance);
        //사용자기록 insert
        pointHistoryTable.insert(id, amount, TransactionType.USE, userPoint.updateMillis());
        return userPoint;
    }


    private void validateUser(long id){
        if (id <= 0) {
            throw new IllegalArgumentException("유효하지 않은 사용자 ID 입니다.");
        }
    }
}
