package com.tranvandang.backend.repository;

import com.tranvandang.backend.entity.Slider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SliderRepository extends JpaRepository<Slider, String> {}
