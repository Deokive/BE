package com.depth.deokive;

import com.depth.deokive.common.enums.Visibility;
import com.depth.deokive.common.util.ThumbnailUtils;
import com.depth.deokive.domain.archive.entity.enums.Badge;
import com.depth.deokive.domain.file.entity.enums.MediaType;
import com.depth.deokive.domain.friend.entity.enums.FriendStatus;
import com.depth.deokive.domain.post.entity.enums.Category;
import com.depth.deokive.domain.user.entity.enums.Role;
import com.depth.deokive.domain.user.entity.enums.UserType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [Deokive Data Initializer - Final Spec Fixed v5]
 * 1. Base Time: LocalDateTime.now()
 * 2. Date Pattern: 2개당 1일씩 감소 ((id-1)/2 days ago)
 * 3. Diary Visibility Logic:
 * - Archive(Public) -> Diary(6:3:1)
 * - Archive(Restricted) -> Diary(9:1)
 * - Archive(Private) -> Diary(Private 100%)
 * 4. File ID Unique: AtomicLong used globally
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ResourceLoader resourceLoader;

    // Constants
    private static final int USER_COUNT = 11;
    private static final int ARCHIVE_COUNT = 100;
    private static final int POST_COUNT = 1050;
    private static final int FILE_POOL_SIZE = 10000;

    // Hot Score Config
    private static final double W1 = 20.0;
    private static final double W2 = 3.0;
    private static final double LAMBDA = 0.004;
    private static final long WINDOW_HOURS = 168;

    // YouTube Playlist JSON Path (classpath resource)
    private static final String YOUTUBE_PLAYLIST_JSON_PATH = "classpath:sample/sample.json";

    // Video Data Cache
    private List<VideoData> videoDataCache = null;

    @Data
    static class VideoData {
        private String title;
        private String url;
        private String thumbnailUrl;
    }

    @Override
    public void run(String... args) {
        if (checkDataExists()) {
            log.info("ℹ️ [Init] 데이터가 이미 존재합니다.");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        log.info("🚀 [Init] 데이터 초기화 시작 (Base Time: {})...", now);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        createUsers(now);
        createFriendMaps(now);

        List<Long> fileIds = createFiles(now);
        AtomicLong fileCursor = new AtomicLong(fileIds.get(0));

        createPosts(fileCursor, now);
        createArchivesAndContents(fileCursor, now);

        stopWatch.stop();
        log.info("✅ [Init] 작업 완료! (Total Time: {}s)", stopWatch.getTotalTimeSeconds());
    }

    private boolean checkDataExists() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class);
        return count != null && count > 0;
    }

    // 1. Users
    private void createUsers(LocalDateTime now) {
        log.info("👉 Step 1. Users Setup");
        String sql = "INSERT INTO users (id, username, email, password, nickname, role, user_type, is_email_verified, created_at, last_modified_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        List<Object[]> batch = new ArrayList<>();
        String pw = passwordEncoder.encode("test_1234!!");
        Timestamp ts = Timestamp.valueOf(now);

        for (long i = 1; i <= USER_COUNT; i++) {
            batch.add(new Object[]{i, "user" + i, "user" + i + "@exam.com", pw, "Tester" + i, Role.USER.name(), UserType.COMMON.name(), true, ts, ts});
        }
        jdbcTemplate.batchUpdate(sql, batch);
    }

    // 2. Friends
    private void createFriendMaps(LocalDateTime now) {
        log.info("👉 Step 2. FriendMap Setup");
        String sql = "INSERT INTO friend_map (user_id, friend_id, requested_by, friend_status, accepted_at, created_at, last_modified_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        List<Object[]> batch = new ArrayList<>();
        Timestamp ts = Timestamp.valueOf(now);

        Map<Long, List<Long>> map = new HashMap<>();
        map.put(1L, List.of(3L, 5L, 7L, 9L));
        map.put(2L, List.of(4L, 6L, 8L, 10L));
        map.put(3L, List.of(1L, 4L));
        map.put(4L, List.of(2L, 3L));
        map.put(5L, List.of(1L));
        map.put(6L, List.of(2L));
        map.put(7L, List.of(1L));
        map.put(8L, List.of(2L));
        map.put(9L, List.of(1L));
        map.put(10L, List.of(2L));

        for (var entry : map.entrySet()) {
            for (Long friendId : entry.getValue()) {
                batch.add(new Object[]{entry.getKey(), friendId, entry.getKey(), FriendStatus.ACCEPTED.name(), ts, ts, ts});
            }
        }
        jdbcTemplate.batchUpdate(sql, batch);
    }

    // 3. Files
    private List<Long> createFiles(LocalDateTime now) {
        log.info("👉 Step 3. File Pool Setup");
        String sql = "INSERT INTO files (s3Object_key, filename, file_size, media_type, is_thumbnail, created_at, last_modified_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        List<Object[]> batch = new ArrayList<>();
        Timestamp ts = Timestamp.valueOf(now);

        long startId = 1L;
        int batchSize = 1000;
        for (int i = 0; i < FILE_POOL_SIZE; i++) {
            String uuid = UUID.randomUUID().toString();
            String key = "files/" + uuid + ".jpg";
            batch.add(new Object[]{key, "sample_" + i + ".jpg", 10240L, MediaType.IMAGE.name(), false, ts, ts});

            if (batch.size() == batchSize) {
                jdbcTemplate.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) jdbcTemplate.batchUpdate(sql, batch);

        List<Long> ids = new ArrayList<>();
        for (long i = 0; i < FILE_POOL_SIZE; i++) ids.add(startId + i);
        return ids;
    }

    // 4. Posts
    private void createPosts(AtomicLong fileCursor, LocalDateTime now) {
        log.info("👉 Step 4. Posts Setup");
        String postSql = "INSERT INTO post (id, title, content, category, user_id, thumbnail_key, created_at, last_modified_at, created_by, last_modified_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String statsSql = "INSERT INTO post_stats (post_id, view_count, like_count, hot_score, category, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        String mapSql = "INSERT INTO post_file_map (post_id, file_id, media_role, sequence, created_at, last_modified_at) VALUES (?, ?, ?, ?, ?, ?)";

        List<Object[]> postBatch = new ArrayList<>();
        List<Object[]> statsBatch = new ArrayList<>();
        List<Object[]> mapBatch = new ArrayList<>();

        long[][] viewLikeSpec = {
                {10000, 300}, {95000, 275}, {90000, 250}, {85000, 225}, {80000, 200},
                {75000, 175}, {70000, 150}, {65000, 125}, {60000, 100}, {55000, 75},
                {50000, 50}, {45000, 25}, {40000, 0}, {35000, 25}, {30000, 50},
                {25000, 75}, {20000, 100}, {15000, 125}, {10000, 150}, {5000, 175}
        };

        // 먼저 모든 파일 ID를 수집
        List<Long> fileIds = new ArrayList<>();
        for (int i = 1; i <= POST_COUNT; i++) {
            fileIds.add(getSafeFileId(fileCursor));
        }
        
        // 파일 ID를 한 번에 조회하여 Map으로 변환
        Map<Long, String> fileKeyMap = new HashMap<>();
        if (!fileIds.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(fileIds.size(), "?"));
            List<Map<String, Object>> fileRows = jdbcTemplate.queryForList(
                    "SELECT id, s3Object_key FROM files WHERE id IN (" + placeholders + ")",
                    fileIds.toArray()
            );
            for (Map<String, Object> row : fileRows) {
                Long id = ((Number) row.get("id")).longValue();
                String key = (String) row.get("s3Object_key");
                fileKeyMap.put(id, key);
            }
        }

        for (int i = 1; i <= POST_COUNT; i++) {
            long userId = (i <= 100) ? ((i - 1) / 10) + 1 : 11L;

            Category category;
            if (i <= 150) category = Category.IDOL;
            else if (i <= 300) category = Category.ACTOR;
            else if (i <= 450) category = Category.MUSICIAN;
            else if (i <= 600) category = Category.SPORT;
            else if (i <= 750) category = Category.ARTIST;
            else if (i <= 900) category = Category.ANIMATION;
            else category = Category.ETC; // 901~1050: ETC for Tester11

            // Date: (id-1)/2 days ago, 같은 날짜면 1초씩 차이
            long daysToSubtract = (i - 1) / 2;
            long secondsToSubtract = (i - 1) % 2; // 같은 날짜면 0 또는 1초 차이
            LocalDateTime createdAt = now.minusDays(daysToSubtract).minusSeconds(secondsToSubtract);
            Timestamp ts = Timestamp.valueOf(createdAt);

            // Stats
            long view = 0, like = 0;
            if (i <= 20) {
                view = viewLikeSpec[i-1][0];
                like = viewLikeSpec[i-1][1];
            }
            double hotScore = calculateHotScore(view, like, createdAt, now);

            long fileId = fileIds.get(i - 1);
            // 파일의 s3ObjectKey를 조회하여 썸네일 키 생성
            String s3ObjectKey = fileKeyMap.get(fileId);
            String thumbKey = ThumbnailUtils.getMediumThumbnailKey(s3ObjectKey);

            postBatch.add(new Object[]{(long)i, "Post " + i, "Content...", category.name(), userId, thumbKey, ts, ts, userId, userId});
            statsBatch.add(new Object[]{(long)i, view, like, hotScore, category.name(), ts});
            mapBatch.add(new Object[]{(long)i, fileId, "PREVIEW", 0, ts, ts});
        }

        jdbcTemplate.batchUpdate(postSql, postBatch);
        jdbcTemplate.batchUpdate(statsSql, statsBatch);
        jdbcTemplate.batchUpdate(mapSql, mapBatch);
    }

    // 5. Archives & Contents
    private void createArchivesAndContents(AtomicLong fileCursor, LocalDateTime now) {
        log.info("👉 Step 5. Archives Setup");

        String archiveSql = "INSERT INTO archive (id, user_id, title, visibility, badge, banner_file_id, thumbnail_key, created_at, last_modified_at, created_by, last_modified_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String statsSql = "INSERT INTO archive_stats (archive_id, view_count, like_count, hot_score, visibility, badge, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";

        String diaryBookSql = "INSERT INTO diary_book (archive_id, title, created_at, last_modified_at) VALUES (?, ?, ?, ?)";
        String galleryBookSql = "INSERT INTO gallery_book (archive_id, title) VALUES (?, ?)";
        String ticketBookSql = "INSERT INTO ticket_book (archive_id, title, created_at, last_modified_at) VALUES (?, ?, ?, ?)";
        String repostBookSql = "INSERT INTO repost_book (archive_id, title, created_at, last_modified_at) VALUES (?, ?, ?, ?)";

        List<Object[]> archiveBatch = new ArrayList<>();
        List<Object[]> statsBatch = new ArrayList<>();
        List<Object[]> diaryBookBatch = new ArrayList<>();
        List<Object[]> galleryBookBatch = new ArrayList<>();
        List<Object[]> ticketBookBatch = new ArrayList<>();
        List<Object[]> repostBookBatch = new ArrayList<>();

        long[][] viewLikeSpec = {
                {10000, 300}, {95000, 275}, {90000, 250}, {85000, 225}, {80000, 200},
                {75000, 175}, {70000, 150}, {65000, 125}, {60000, 100}, {55000, 75},
                {50000, 50}, {45000, 25}, {40000, 0}, {35000, 25}, {30000, 50},
                {25000, 75}, {20000, 100}, {15000, 125}, {10000, 150}, {5000, 175}
        };

        // 먼저 모든 파일 ID를 수집
        List<Long> archiveFileIds = new ArrayList<>();
        for (int i = 1; i <= ARCHIVE_COUNT; i++) {
            archiveFileIds.add(getSafeFileId(fileCursor));
        }
        
        // 파일 ID를 한 번에 조회하여 Map으로 변환
        Map<Long, String> archiveFileKeyMap = new HashMap<>();
        if (!archiveFileIds.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(archiveFileIds.size(), "?"));
            List<Map<String, Object>> fileRows = jdbcTemplate.queryForList(
                    "SELECT id, s3Object_key FROM files WHERE id IN (" + placeholders + ")",
                    archiveFileIds.toArray()
            );
            for (Map<String, Object> row : fileRows) {
                Long id = ((Number) row.get("id")).longValue();
                String key = (String) row.get("s3Object_key");
                archiveFileKeyMap.put(id, key);
            }
        }

        for (int i = 1; i <= ARCHIVE_COUNT; i++) {
            long archiveId = i;
            long userId = (long) ((i - 1) / 10) + 1;

            Visibility vis;
            if (i <= 60) vis = Visibility.PUBLIC;
            else if (i <= 90) vis = Visibility.RESTRICTED;
            else vis = Visibility.PRIVATE;

            long view = 0, like = 0;
            if (i <= 20) {
                view = viewLikeSpec[i-1][0];
                like = viewLikeSpec[i-1][1];
            }

            // Date: (id-1)/2 days ago, 같은 날짜면 1초씩 차이
            long daysToSubtract = (i - 1) / 2;
            long secondsToSubtract = (i - 1) % 2; // 같은 날짜면 0 또는 1초 차이
            LocalDateTime createdAt = now.minusDays(daysToSubtract).minusSeconds(secondsToSubtract);
            Timestamp ts = Timestamp.valueOf(createdAt);

            double hotScore = calculateHotScore(view, like, createdAt, now);
            long fileId = archiveFileIds.get(i - 1);
            // 파일의 s3ObjectKey를 조회하여 썸네일 키 생성
            String s3ObjectKey = archiveFileKeyMap.get(fileId);
            String thumbKey = ThumbnailUtils.getMediumThumbnailKey(s3ObjectKey);

            archiveBatch.add(new Object[]{archiveId, userId, "Archive " + i, vis.name(), Badge.NEWBIE.name(), fileId, thumbKey, ts, ts, userId, userId});
            statsBatch.add(new Object[]{archiveId, view, like, hotScore, vis.name(), Badge.NEWBIE.name(), ts});

            diaryBookBatch.add(new Object[]{archiveId, "DiaryBook", ts, ts});
            galleryBookBatch.add(new Object[]{archiveId, "GalleryBook"});
            ticketBookBatch.add(new Object[]{archiveId, "TicketBook", ts, ts});
            repostBookBatch.add(new Object[]{archiveId, "RepostBook", ts, ts});
        }

        jdbcTemplate.batchUpdate(archiveSql, archiveBatch);
        jdbcTemplate.batchUpdate(statsSql, statsBatch);
        jdbcTemplate.batchUpdate(diaryBookSql, diaryBookBatch);
        jdbcTemplate.batchUpdate(galleryBookSql, galleryBookBatch);
        jdbcTemplate.batchUpdate(ticketBookSql, ticketBookBatch);
        jdbcTemplate.batchUpdate(repostBookSql, repostBookBatch);

        createSubContents(fileCursor, now);
    }

    private void createSubContents(AtomicLong fileCursor, LocalDateTime now) {
        log.info("Creating Sub-Contents...");

        String diarySql = "INSERT INTO diary (diary_book_id, title, content, recorded_at, color, visibility, created_at, last_modified_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String gallerySql = "INSERT INTO gallery (archive_id, gallery_book_id, file_id, original_key, created_at, last_modified_at) VALUES (?, ?, ?, ?, ?, ?)";
        String ticketSql = "INSERT INTO ticket (ticket_book_id, title, date, location, created_at, last_modified_at) VALUES (?, ?, ?, ?, ?, ?)";
        String repostTabSql = "INSERT INTO repost_tab (id, repost_book_id, title, created_at, last_modified_at) VALUES (?, ?, ?, ?, ?)";
        String repostSql = "INSERT INTO repost (repost_tab_id, url, url_hash, title, thumbnail_url, status, created_at, last_modified_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        // Diaries: one archive has either 0 or 100 diaries.
        // Here we generate 100 diaries for every archive(=diary_book_id) to match visibility distribution spec:
        // - PUBLIC archive: PUBLIC:RESTRICTED:PRIVATE = 6:3:1 per 10 diaries (=> 60/30/10 per 100)
        // - RESTRICTED archive: RESTRICTED:PRIVATE = 9:1 per 10 diaries (=> 90/10 per 100)
        // - PRIVATE archive: PRIVATE 100%
        for (long archiveId = 1L; archiveId <= ARCHIVE_COUNT; archiveId++) {
            int start = (int) ((archiveId - 1) * 100 + 1);
            int end = (int) (archiveId * 100);
            insertDiaries(diarySql, archiveId, start, end, now);
        }

        insertGalleries(gallerySql, 1L, 1, 100, now, fileCursor);
        insertTickets(ticketSql, 1L, 1, 100, now);
        insertReposts(repostTabSql, repostSql, 1L, 1, 100, now);

        insertGalleries(gallerySql, 2L, 101, 200, now, fileCursor);
        insertTickets(ticketSql, 2L, 101, 200, now);
        insertReposts(repostTabSql, repostSql, 2L, 101, 200, now);

        insertTickets(ticketSql, 3L, 201, 300, now);
        insertReposts(repostTabSql, repostSql, 3L, 201, 300, now);

        insertGalleries(gallerySql, 4L, 201, 300, now, fileCursor);
        insertReposts(repostTabSql, repostSql, 4L, 301, 400, now);

        insertGalleries(gallerySql, 5L, 301, 400, now, fileCursor);
        insertTickets(ticketSql, 5L, 301, 400, now);

        insertTickets(ticketSql, 6L, 401, 500, now);
        insertReposts(repostTabSql, repostSql, 6L, 401, 500, now);

        insertGalleries(gallerySql, 7L, 401, 500, now, fileCursor);
        insertReposts(repostTabSql, repostSql, 7L, 501, 600, now);

        insertGalleries(gallerySql, 8L, 501, 600, now, fileCursor);
        insertTickets(ticketSql, 8L, 501, 600, now);

        insertReposts(repostTabSql, repostSql, 9L, 601, 700, now);

        insertTickets(ticketSql, 10L, 601, 700, now);

        insertGalleries(gallerySql, 11L, 601, 700, now, fileCursor);

        insertGalleries(gallerySql, 13L, 701, 800, now, fileCursor);

        insertTickets(ticketSql, 14L, 701, 800, now);

        insertReposts(repostTabSql, repostSql, 15L, 701, 800, now);
    }

    private Timestamp getArchiveDate(Long archiveId, LocalDateTime baseTime) {
        long daysToSubtract = (archiveId - 1) / 2;
        long secondsToSubtract = (archiveId - 1) % 2; // 같은 날짜면 0 또는 1초 차이
        return Timestamp.valueOf(baseTime.minusDays(daysToSubtract).minusSeconds(secondsToSubtract));
    }

    private Visibility getArchiveVisibility(Long archiveId) {
        if (archiveId <= 60) return Visibility.PUBLIC;
        else if (archiveId <= 90) return Visibility.RESTRICTED;
        else return Visibility.PRIVATE;
    }

    // [Fix] Diary Visibility Distribution (6:3:1) Logic
    private void insertDiaries(String sql, Long bookId, int start, int end, LocalDateTime baseTime) {
        Timestamp now = getArchiveDate(bookId, baseTime);
        Visibility archiveVis = getArchiveVisibility(bookId);

        List<Object[]> batch = new ArrayList<>();

        for (int i = start; i <= end; i++) {
            Visibility diaryVis;
            // 로컬 인덱스 (0, 1, 2...)
            int localIdx = i - start;

            if (archiveVis == Visibility.PUBLIC) {
                // Public Archive: Public(6) : Restricted(3) : Private(1)
                int mod = localIdx % 10;
                if (mod < 6) diaryVis = Visibility.PUBLIC;
                else if (mod < 9) diaryVis = Visibility.RESTRICTED;
                else diaryVis = Visibility.PRIVATE;

            } else if (archiveVis == Visibility.RESTRICTED) {
                // Restricted Archive: Restricted(9) : Private(1)
                int mod = localIdx % 10;
                if (mod < 9) diaryVis = Visibility.RESTRICTED;
                else diaryVis = Visibility.PRIVATE;

            } else {
                // Private Archive: Private(10)
                diaryVis = Visibility.PRIVATE;
            }

            batch.add(new Object[]{bookId, "Diary " + i, "Content...", Timestamp.valueOf(baseTime), "#FF5733", diaryVis.name(), now, now});
        }
        jdbcTemplate.batchUpdate(sql, batch);
    }

    private void insertGalleries(String sql, Long archiveId, int start, int end, LocalDateTime baseTime, AtomicLong fileCursor) {
        Timestamp now = getArchiveDate(archiveId, baseTime);
        List<Object[]> batch = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            long fileId = getSafeFileId(fileCursor);
            batch.add(new Object[]{archiveId, archiveId, fileId, "files/dummy_gallery_" + i + ".jpg", now, now});
        }
        jdbcTemplate.batchUpdate(sql, batch);
    }

    private void insertTickets(String sql, Long bookId, int start, int end, LocalDateTime baseTime) {
        Timestamp now = getArchiveDate(bookId, baseTime);
        List<Object[]> batch = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            batch.add(new Object[]{bookId, "Ticket " + i, Timestamp.valueOf(baseTime), "Seoul", now, now});
        }
        jdbcTemplate.batchUpdate(sql, batch);
    }

    private void insertReposts(String tabSql, String repostSql, Long bookId, int start, int end, LocalDateTime baseTime) {
        Timestamp now = getArchiveDate(bookId, baseTime);
        long tabId = bookId * 100;
        jdbcTemplate.update(tabSql, tabId, bookId, "My Tab", now, now);

        // Load video data from JSON (cached)
        List<VideoData> videos = loadYoutubePlaylistData();
        if (videos.isEmpty()) {
            log.warn("⚠️ YouTube playlist data is empty. Skipping repost creation for bookId={}", bookId);
            return;
        }

        List<Object[]> batch = new ArrayList<>();
        int videoCount = videos.size();

        for (int i = start; i <= end; i++) {
            // Circular indexing: if data runs out, loop back to index 0
            int videoIndex = (i - start) % videoCount;
            VideoData video = videos.get(videoIndex);

            batch.add(new Object[]{
                    tabId,
                    video.getUrl(),
                    generateUrlHash(video.getUrl()),
                    video.getTitle(),
                    video.getThumbnailUrl(),
                    "COMPLETED",
                    now,
                    now
            });
        }
        jdbcTemplate.batchUpdate(repostSql, batch);
    }

    /**
     * Load YouTube playlist data from JSON file (with caching)
     * Uses classpath resource to work in both local and production environments
     */
    private List<VideoData> loadYoutubePlaylistData() {
        // Return cached data if already loaded
        if (videoDataCache != null) {
            return videoDataCache;
        }

        List<VideoData> videos = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            Resource resource = resourceLoader.getResource(YOUTUBE_PLAYLIST_JSON_PATH);
            if (!resource.exists()) {
                log.warn("⚠️ YouTube playlist JSON not found at: {}", YOUTUBE_PLAYLIST_JSON_PATH);
                return videos;
            }

            JsonNode root;
            try (InputStream inputStream = resource.getInputStream()) {
                root = objectMapper.readTree(inputStream);
            }

            JsonNode videosNode = root.get("videos");

            if (videosNode != null && videosNode.isArray()) {
                for (JsonNode videoNode : videosNode) {
                    VideoData video = new VideoData();
                    video.setTitle(videoNode.get("title").asText());
                    video.setUrl(videoNode.get("url").asText());
                    video.setThumbnailUrl(videoNode.get("thumbnailUrl").asText());
                    videos.add(video);
                }
            }

            videoDataCache = videos; // Cache for reuse
            log.info("✅ Loaded {} videos from YouTube playlist JSON", videos.size());

        } catch (IOException e) {
            log.error("❌ Failed to load YouTube playlist JSON: {}", e.getMessage(), e);
        }

        return videos;
    }

    private long getSafeFileId(AtomicLong fileCursor) {
        long id = fileCursor.getAndIncrement();
        if (id > FILE_POOL_SIZE) {
            fileCursor.set(2);
            return 1;
        }
        return id;
    }

    private String generateUrlHash(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 알고리즘을 찾을 수 없습니다.", e);
        }
    }

    private double calculateHotScore(long view, long like, LocalDateTime createdAt, LocalDateTime nowTime) {
        long ageHours = ChronoUnit.HOURS.between(createdAt, nowTime);
        if (ageHours < 0) ageHours = 0;

        double termLike = W1 * Math.log(1 + like);
        double termView = W2 * Math.log(1 + view);
        double decay;

        if (ageHours <= WINDOW_HOURS) {
            decay = Math.exp(-LAMBDA * ageHours);
            return (termLike + termView) * decay;
        } else {
            decay = Math.exp(-LAMBDA * WINDOW_HOURS);
            double baseScore = (termLike + termView) * decay;
            return baseScore * 0.5;
        }
    }
}