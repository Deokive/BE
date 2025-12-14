package com.depth.deokive.domain.file.repository;

import com.depth.deokive.domain.file.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<File, Long> {
    List<File> findByIdIn(List<Long> ids);
    Optional<File> findByS3ObjectKey(String s3ObjectKey);
}
