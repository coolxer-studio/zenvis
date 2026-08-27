package com.coolxer.dao.mysql.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;

/**
 * JPA基类
 */
@NoRepositoryBean
public interface BaseRepository<T, I extends Serializable> extends JpaRepository<T, Serializable> {

}
