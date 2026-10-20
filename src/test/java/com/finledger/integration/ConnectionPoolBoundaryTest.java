package com.finledger.integration;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("pooltest")
@Import(ConnectionPoolBoundaryTest.PoolTestConfig.class)
public class ConnectionPoolBoundaryTest {
    @Autowired
    BadService badService;
    @Autowired
    GoodService goodService;
    @Autowired
    HikariDataSource dataSource;

    //공통 대기 도구: 외부 은행의 느린 응답을 모의
    private static void awaitRelease(CountDownLatch release){
        try{
            if(!release.await(8, TimeUnit.SECONDS)){
                throw new IllegalArgumentException("대기 시간 초과");
            }
        } catch (InterruptedException e){
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException(e);
        }
    }

    static class BadService{
        private final JdbcTemplate jdbcTemplate;
        BadService(JdbcTemplate jdbcTemplate){
            this.jdbcTemplate = jdbcTemplate;
        }

        @Transactional
        public void execute(CountDownLatch entered, CountDownLatch release){
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            //DB 커넥션을 확보한 상태
            entered.countDown();

            //외부 은행 응답 대기
            awaitRelease(release);
        }
    }

    //개선 후: DB 작업만 짧은 트랜잭션으로 실행
    static class DbStepService{
        private final JdbcTemplate jdbcTemplate;

        DbStepService(JdbcTemplate jdbcTemplate){
            this.jdbcTemplate = jdbcTemplate;
        }

        @Transactional
        public void execute(){
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        }
    }

    static class GoodService{
        private final DbStepService dbStepService;

        GoodService(DbStepService dbStepService){
            this.dbStepService = dbStepService;
        }

        //여기에는 @Transactional을 붙이지 않음
        public void execute(CountDownLatch entered, CountDownLatch release){

            //외부 응답을 기다리는 동안 DB 연결 불필요
            entered.countDown();
            awaitRelease(release);

            //응답 이후 DB 트랜잭션 시작
            dbStepService.execute();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PoolTestConfig{
        @Bean
        BadService badService(JdbcTemplate jdbcTemplate){
            return new BadService(jdbcTemplate);
        }

        @Bean
        DbStepService dbStepService(JdbcTemplate jdbcTemplate){
            return new DbStepService(jdbcTemplate);
        }

        @Bean
        GoodService goodService(DbStepService dbStepService){
            return new GoodService(dbStepService);
        }
    }

    @Test
    @DisplayName("긴 트랜잭션은 풀 고갈을 유발한다")
    void shouldExhaustPoolWhenHoldingConnections() throws Exception{
        ExecutorService executor = Executors.newFixedThreadPool(6);

        CountDownLatch entered = new CountDownLatch(5);
        CountDownLatch release = new CountDownLatch(1);

        List<Future<?>> holders = new ArrayList<>();

        try{
            //5개 트랜잭션이 DB 커넥션을 점유
            for(int i = 0; i < 5; i++){
                holders.add(executor.submit(() -> badService.execute(entered,release)));
            }

            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            int active = dataSource.getHikariPoolMXBean().getActiveConnections();

            System.out.println("활성 커넥션" + active);

            assertThat(active).isEqualTo(5);

            //여섯 번째 요청은 DB 연결 획득을 시도
            Future<?> extra = executor.submit(()-> badService.execute(new CountDownLatch(0), release));

            ExecutionException exception = assertThrows(ExecutionException.class, () -> extra.get(5, TimeUnit.SECONDS));

            Throwable root = exception.getCause();

            while(root.getCause() != null){
                root = root.getCause();
            }

            assertThat(root).isInstanceOf(SQLException.class);

            System.out.println("커넥션 획득 실패: " + root.getMessage());
        } finally {
            release.countDown();

            for(Future<?> future : holders){
                future.get(10, TimeUnit.SECONDS);
            }

            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("외부 호출과 DB 트랜잭션을 분리하면 커넥션 고갈을 방지한다")
    void shouldCompleteRequestsWithShortTransactions() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(6);

        CountDownLatch entered = new CountDownLatch(6);
        CountDownLatch release = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();

        try{
            for(int i = 0 ; i < 6; i++){
                futures.add(executor.submit(()-> goodService.execute(entered, release)));
            }

            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            //6개 요청 모두 외부 응답을 기다리고 있음
            int active = dataSource.getHikariPoolMXBean().getActiveConnections();

            System.out.println("외부 대기 중 활성 : " + active);

            assertThat(active).isZero();

            //외부 응답 도착 후 DB 작업 실행
            release.countDown();

            for(Future<?> future : futures){
                future.get(10, TimeUnit.SECONDS);
            }

            System.out.println("성공한 요청 수 : 6");

        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

}
