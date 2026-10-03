package com.skylanka.air.shared.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "system_settings")
public class SystemSetting {
    @Id
    @Column(name = "setting_key", length = 80)
    private String key;
    @Column(name = "setting_value", nullable = false, length = 200)
    private String value;

    public SystemSetting() {
    }

    public SystemSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String v) {
        value = v;
    }
}
