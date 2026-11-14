package org.epiccure.mtg.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_setting")
@Getter @Setter @NoArgsConstructor
public class AppSetting {
    @Id @Column(name = "setting_key") private String key;
    @Column(name = "setting_value", nullable = false) private String value;
}
