package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PointServiceIntegrationTest {
    private PointService pointService;
    private UserPointTable userPointTable;
    private PointHistoryTable pointHistoryTable;

    @BeforeEach
    void setUp() {
        // Mock이 아닌 실제 구현체 사용
        userPointTable = new UserPointTable();
        pointHistoryTable = new PointHistoryTable();
        pointService = new PointService(userPointTable, pointHistoryTable);
    }

    @Test
    @DisplayName("통합테스트 - 포인트 충전 후 조회")
    void 포인트_충전_후_조회() {
        // Given
        long userId = 1L;
        long chargeAmount = 1000L;

        // When
        UserPoint charged = pointService.chargePoint(userId, chargeAmount);
        UserPoint result = pointService.getUserPoint(userId);

        // Then
        assertThat(charged.point()).isEqualTo(1000L);
        assertThat(result.point()).isEqualTo(1000L);
        assertThat(result.id()).isEqualTo(userId);
    }

    @Test
    @DisplayName("통합테스트 - 포인트 충전 후 사용")
    void 포인트_충전_후_사용() {
        // Given
        long userId = 2L;
        long chargeAmount = 1000L;
        long useAmount = 300L;

        // When
        UserPoint charged = pointService.chargePoint(userId, chargeAmount);
        UserPoint used = pointService.usePoint(userId, useAmount);

        // Then
        assertThat(charged.point()).isEqualTo(1000L);
        assertThat(used.point()).isEqualTo(700L);
    }

    @Test
    @DisplayName("통합테스트 - 포인트 충전/사용 후 내역 조회")
    void 포인트_충전_사용_후_내역조회() {
        // Given
        long userId = 3L;
        long chargeAmount = 2000L;
        long useAmount = 500L;

        // When
        pointService.chargePoint(userId, chargeAmount);
        pointService.usePoint(userId, useAmount);
        List<PointHistory> histories = pointService.getUserHistories(userId);

        // Then
        assertThat(histories).hasSize(2);
        assertThat(histories.get(0).type()).isEqualTo(TransactionType.CHARGE);
        assertThat(histories.get(0).amount()).isEqualTo(chargeAmount);
        assertThat(histories.get(1).type()).isEqualTo(TransactionType.USE);
        assertThat(histories.get(1).amount()).isEqualTo(useAmount);
    }

    @Test
    @DisplayName("통합테스트 - 여러번 충전 후 포인트 누적 확인")
    void 여러번_충전_후_포인트_누적() {
        // Given
        long userId = 4L;
        long firstCharge = 1000L;
        long secondCharge = 2000L;
        long thirdCharge = 3000L;

        // When
        pointService.chargePoint(userId, firstCharge);
        pointService.chargePoint(userId, secondCharge);
        UserPoint result = pointService.chargePoint(userId, thirdCharge);

        // Then
        assertThat(result.point()).isEqualTo(6000L);
    }

    @Test
    @DisplayName("통합테스트 - 충전/사용 반복 후 최종 잔액 확인")
    void 충전_사용_반복_후_최종잔액() {
        // Given
        long userId = 5L;

        // When
        pointService.chargePoint(userId, 5000L);  // 5000
        pointService.usePoint(userId, 1000L);     // 4000
        pointService.chargePoint(userId, 3000L);  // 7000
        pointService.usePoint(userId, 2000L);     // 5000
        UserPoint finalPoint = pointService.getUserPoint(userId);

        // Then
        assertThat(finalPoint.point()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("통합테스트 - 충전/사용 후 내역 순서 확인")
    void 충전_사용_후_내역순서_확인() {
        // Given
        long userId = 6L;

        // When
        pointService.chargePoint(userId, 1000L);
        pointService.chargePoint(userId, 2000L);
        pointService.usePoint(userId, 500L);
        pointService.chargePoint(userId, 1000L);
        pointService.usePoint(userId, 300L);

        List<PointHistory> histories = pointService.getUserHistories(userId);

        // Then
        assertThat(histories).hasSize(5);
        assertThat(histories.get(0).type()).isEqualTo(TransactionType.CHARGE);
        assertThat(histories.get(0).amount()).isEqualTo(1000L);
        assertThat(histories.get(1).type()).isEqualTo(TransactionType.CHARGE);
        assertThat(histories.get(1).amount()).isEqualTo(2000L);
        assertThat(histories.get(2).type()).isEqualTo(TransactionType.USE);
        assertThat(histories.get(2).amount()).isEqualTo(500L);
        assertThat(histories.get(3).type()).isEqualTo(TransactionType.CHARGE);
        assertThat(histories.get(3).amount()).isEqualTo(1000L);
        assertThat(histories.get(4).type()).isEqualTo(TransactionType.USE);
        assertThat(histories.get(4).amount()).isEqualTo(300L);
    }

    @Test
    @DisplayName("통합테스트 - 다른 유저간 포인트 독립성 확인")
    void 다른_유저간_포인트_독립성() {
        // Given
        long user1 = 7L;
        long user2 = 8L;

        // When
        pointService.chargePoint(user1, 1000L);
        pointService.chargePoint(user2, 2000L);
        pointService.usePoint(user1, 300L);

        UserPoint user1Point = pointService.getUserPoint(user1);
        UserPoint user2Point = pointService.getUserPoint(user2);

        // Then
        assertThat(user1Point.point()).isEqualTo(700L);
        assertThat(user2Point.point()).isEqualTo(2000L);
    }
}
