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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class PointServiceTest {
    //포인트 최대값
    private static final long POINT_MAX = 10000L;
    //포인트 최소값
    private static final long POINT_MIN = 1L;

    private PointService pointService;
    private UserPointTable userPointTable;
    private PointHistoryTable pointHistoryTable;


    @BeforeEach
    void setUp() {
        // Mock 객체 생성
        userPointTable = mock(UserPointTable.class);
        pointHistoryTable = mock(PointHistoryTable.class);

        // PointService에 Mock 주입
        pointService = new PointService(userPointTable, pointHistoryTable);
    }

    @Test
    @DisplayName("포인트 조회 - 특정 유저의 포인트를 조회한다")
    void getUserPoint_정상조회() {
        // Given
        long userId = 1L;
        long expectedPoint = 1000L;
        long currentTime = System.currentTimeMillis();
        UserPoint expectedUserPoint = new UserPoint(userId, expectedPoint, currentTime);

        when(userPointTable.selectById(userId)).thenReturn(expectedUserPoint);

        // When
        UserPoint result = pointService.getUserPoint(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(expectedPoint);
        verify(userPointTable, times(1)).selectById(userId);
    }

    @Test
    @DisplayName("포인트조회 예외케이스 - 음수 ID")
    void getUserPoint_Minus_ID(){
        // Given
        long invalidId = -1L;

        //when & then
        assertThatThrownBy(()-> pointService.getUserPoint(invalidId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 사용자 ID 입니다.");

    }
    @Test
    @DisplayName("포인트조회 예외케이스 - 0 ID")
    void getUserPoint_Zero_ID(){
        // Given
        long invalidId = 0L;

        //when & then
        assertThatThrownBy(()-> pointService.getUserPoint(invalidId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 사용자 ID 입니다.");
    }

    @Test
    @DisplayName("포인트충전 - 특정 유저의 포인트를 충전한다")
    void chargePoint_정상충전() {
        // Given
        long userId = 1L;
        long currentPoint = 1000L;
        long chargeAmount = 500L;
        long expectedPoint = 1500L;
        long currentTime = System.currentTimeMillis();

        UserPoint currentUserPoint = new UserPoint(userId, currentPoint, currentTime);
        UserPoint chargedUserPoint = new UserPoint(userId, expectedPoint, currentTime);

        when(userPointTable.selectById(userId)).thenReturn(currentUserPoint);
        when(userPointTable.insertOrUpdate(userId, expectedPoint)).thenReturn(chargedUserPoint);

        // When
        UserPoint result = pointService.chargePoint(userId, chargeAmount);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.point()).isEqualTo(expectedPoint);
        verify(userPointTable, times(1)).selectById(userId);
        verify(userPointTable, times(1)).insertOrUpdate(userId, expectedPoint);
        verify(pointHistoryTable, times(1)).insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    @DisplayName("포인트충전 - 포인트 합산 값 최대 값 초과")
    void chargePoint_Sum_Max_value_over() {
        // Given
        long userId = 1L;
        long currentPoint = 90000L;
        long chargeAmount = 20000L;  // 90000 + 20000 = 110000 > 100000
        long currentTime = System.currentTimeMillis();

        UserPoint currentUserPoint = new UserPoint(userId, currentPoint, currentTime);
        when(userPointTable.selectById(userId)).thenReturn(currentUserPoint);

        // When & Then
        assertThatThrownBy(() -> pointService.chargePoint(userId, chargeAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("포인트 최대값을 초과했습니다.");  // "최대 포인트" 또는 "최대값" 포함
    }

    @Test
    @DisplayName("포인트충전 - 포인트 값이 1보다 작은경우")
    void chargePoint_Point_Under_MinPoint(){
        //Given
        long id =1L;
        long amount = -1000L;

        //when&then
        assertThatThrownBy(() -> pointService.chargePoint(id, amount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("포인트 충전 최소금액은 1 이상이여야 합니다.");
    }



    @Test
    @DisplayName("포인트충전 예외케이스 - 음수 ID")
    void chargePoint_Minus_ID(){
        // Given
        long invalidId = -1L;
        long amount = 500L;

        //when & then
        assertThatThrownBy(()-> pointService.chargePoint(invalidId,amount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 사용자 ID 입니다.");
    }

    @Test
    @DisplayName("포인트충전 예외케이스 - 0 ID")
    void chargePoint_Zero_ID(){
        // Given
        long invalidId = 0L;
        long amount = 500L;

        //when & then
        assertThatThrownBy(()-> pointService.chargePoint(invalidId,amount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 사용자 ID 입니다.");
    }

    @Test
    @DisplayName("포인트사용 - 특정 유저의 포인트를 사용한다")
    void usePoint_정상사용() {
        // Given
        long userId = 1L;
        long currentPoint = 1000L;
        long useAmount = 300L;
        long expectedPoint = 700L;
        long currentTime = System.currentTimeMillis();

        UserPoint currentUserPoint = new UserPoint(userId, currentPoint, currentTime);
        UserPoint usedUserPoint = new UserPoint(userId, expectedPoint, currentTime);

        when(userPointTable.selectById(userId)).thenReturn(currentUserPoint);
        when(userPointTable.insertOrUpdate(userId, expectedPoint)).thenReturn(usedUserPoint);

        // When
        UserPoint result = pointService.usePoint(userId, useAmount);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.point()).isEqualTo(expectedPoint);
        verify(userPointTable, times(1)).selectById(userId);
        verify(userPointTable, times(1)).insertOrUpdate(userId, expectedPoint);
        verify(pointHistoryTable, times(1)).insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong());
    }

    @Test
    @DisplayName("포인트사용 예외케이스 - 음수 ID")
    void usePoint_Minus_ID(){
        // Given
        long invalidId = -1L;
        long useAmonunt = 600L;

        //when & then
        assertThatThrownBy(()-> pointService.usePoint(invalidId, useAmonunt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 사용자 ID 입니다.");

    }

    @Test
    @DisplayName("포인트사용 예외케이스 - 0 ID")
    void usePoint_Zero_ID(){
        // Given
        long invalidId = 0L;
        long useAmonunt = 600L;

        //when & then
        assertThatThrownBy(()-> pointService.usePoint(invalidId, useAmonunt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 사용자 ID 입니다.");
    }

    @Test
    @DisplayName("포인트내역조회 - 특정 유저의 포인트 충전/사용 내역을 조회한다")
    void getUserHistories_정상조회() {
        // Given
        long userId = 1L;
        long currentTime = System.currentTimeMillis();

        List<PointHistory> expectedHistories = List.of(
                new PointHistory(1L, userId, 1000L, TransactionType.CHARGE, currentTime),
                new PointHistory(2L, userId, 300L, TransactionType.USE, currentTime)
        );

        when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(expectedHistories);

        // When
        List<PointHistory> result = pointService.getUserHistories(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).type()).isEqualTo(TransactionType.CHARGE);
        assertThat(result.get(1).type()).isEqualTo(TransactionType.USE);
        verify(pointHistoryTable, times(1)).selectAllByUserId(userId);
    }

    @Test
    @DisplayName("포인트내역조회 예외케이스 - 음수 ID")
    void getUserHistories__Minus_ID(){
        // Given
        long invalidId = -1L;

        //when & then
        assertThatThrownBy(()-> pointService.getUserHistories(invalidId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 사용자 ID 입니다.");

    }

    @Test
    @DisplayName("포인트내역조회 예외케이스 - 0 ID")
    void getUserHistories__Zero_ID(){
        // Given
        long invalidId = 0L;

        //when & then
        assertThatThrownBy(()-> pointService.getUserHistories(invalidId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 사용자 ID 입니다.");
    }

}
