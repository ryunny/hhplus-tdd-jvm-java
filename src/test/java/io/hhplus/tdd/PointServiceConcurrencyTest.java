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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class PointServiceConcurrencyTest {
    private PointService pointService;
    private UserPointTable userPointTable;
    private PointHistoryTable pointHistoryTable;

    @BeforeEach
    void setUp() {
        userPointTable = new UserPointTable();
        pointHistoryTable = new PointHistoryTable();
        pointService = new PointService(userPointTable, pointHistoryTable);
    }

    @Test
    @DisplayName("동시성 테스트 - 동시에 여러 번 충전")
    void 동시에_여러번_충전() throws InterruptedException {
        // Given
        long userId = 1L;
        long chargeAmount = 100L;
        int threadCount = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // When
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    pointService.chargePoint(userId, chargeAmount);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();

        // Then
        UserPoint result = pointService.getUserPoint(userId);
        assertThat(result.point()).isEqualTo(chargeAmount * threadCount); // 100 * 10 = 1000
    }

    @Test
    @DisplayName("동시성 테스트 - 동시에 충전과 사용")
    void 동시에_충전과_사용() throws InterruptedException {
        // Given
        long userId = 2L;
        long initialCharge = 10000L;
        pointService.chargePoint(userId, initialCharge);

        int chargeThreadCount = 5;
        int useThreadCount = 3;
        long chargeAmount = 1000L;
        long useAmount = 500L;

        ExecutorService executorService = Executors.newFixedThreadPool(chargeThreadCount + useThreadCount);
        CountDownLatch latch = new CountDownLatch(chargeThreadCount + useThreadCount);

        // When
        // 충전 스레드
        for (int i = 0; i < chargeThreadCount; i++) {
            executorService.execute(() -> {
                try {
                    pointService.chargePoint(userId, chargeAmount);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 사용 스레드
        for (int i = 0; i < useThreadCount; i++) {
            executorService.execute(() -> {
                try {
                    pointService.usePoint(userId, useAmount);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then
        UserPoint result = pointService.getUserPoint(userId);
        long expectedPoint = initialCharge + (chargeAmount * chargeThreadCount) - (useAmount * useThreadCount);
        // 10000 + (1000 * 5) - (500 * 3) = 10000 + 5000 - 1500 = 13500
        assertThat(result.point()).isEqualTo(expectedPoint);
    }

    @Test
    @DisplayName("동시성 테스트 - 여러 사용자 동시 충전")
    void 여러_사용자_동시_충전() throws InterruptedException {
        // Given
        int userCount = 5;
        int chargePerUser = 3;
        long chargeAmount = 1000L;

        ExecutorService executorService = Executors.newFixedThreadPool(userCount * chargePerUser);
        CountDownLatch latch = new CountDownLatch(userCount * chargePerUser);

        // When
        for (long userId = 1; userId <= userCount; userId++) {
            long finalUserId = userId;
            for (int i = 0; i < chargePerUser; i++) {
                executorService.execute(() -> {
                    try {
                        pointService.chargePoint(finalUserId, chargeAmount);
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        latch.await();
        executorService.shutdown();

        // Then
        for (long userId = 1; userId <= userCount; userId++) {
            UserPoint result = pointService.getUserPoint(userId);
            assertThat(result.point()).isEqualTo(chargeAmount * chargePerUser); // 1000 * 3 = 3000
        }
    }

    @Test
    @DisplayName("동시성 테스트 - 동시 사용 시 잔액 부족 처리")
    void 동시_사용_시_잔액부족_처리() throws InterruptedException {
        // Given
        long userId = 3L;
        long initialCharge = 1000L;
        pointService.chargePoint(userId, initialCharge);

        int threadCount = 10;
        long useAmount = 200L;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    pointService.usePoint(userId, useAmount);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then
        UserPoint result = pointService.getUserPoint(userId);
        // 1000원으로 200원씩 사용하면 5번 성공, 5번 실패
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);
        assertThat(result.point()).isEqualTo(0L);
    }

    @Test
    @DisplayName("동시성 테스트 - 충전/사용 내역 정확성")
    void 충전_사용_내역_정확성() throws InterruptedException {
        // Given
        long userId = 4L;
        int chargeCount = 5;
        int useCount = 3;
        long chargeAmount = 1000L;
        long useAmount = 500L;

        ExecutorService executorService = Executors.newFixedThreadPool(chargeCount + useCount);
        CountDownLatch latch = new CountDownLatch(chargeCount + useCount);

        // When
        for (int i = 0; i < chargeCount; i++) {
            executorService.execute(() -> {
                try {
                    pointService.chargePoint(userId, chargeAmount);
                } finally {
                    latch.countDown();
                }
            });
        }

        for (int i = 0; i < useCount; i++) {
            executorService.execute(() -> {
                try {
                    pointService.usePoint(userId, useAmount);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then
        List<PointHistory> histories = pointService.getUserHistories(userId);
        assertThat(histories).hasSize(chargeCount + useCount); // 8개

        long chargeHistoryCount = histories.stream()
                .filter(h -> h.type() == TransactionType.CHARGE)
                .count();
        long useHistoryCount = histories.stream()
                .filter(h -> h.type() == TransactionType.USE)
                .count();

        assertThat(chargeHistoryCount).isEqualTo(chargeCount);
        assertThat(useHistoryCount).isEqualTo(useCount);
    }
}
