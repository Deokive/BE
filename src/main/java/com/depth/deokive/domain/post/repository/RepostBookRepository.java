package com.depth.deokive.domain.post.repository;

import com.depth.deokive.domain.archive.entity.Archive;
import com.depth.deokive.domain.post.entity.RepostBook;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepostBookRepository extends JpaRepository<RepostBook, Long> {
    Long archive(Archive archive);
}
