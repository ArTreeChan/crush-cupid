package cn.yzfy.crushApp.ui;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import cn.yzfy.crushApp.R;

/** 极简 Fragment 导航。 */
public final class Nav {
    private Nav() {
    }

    public static void push(FragmentActivity a, Fragment f) {
        a.getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                        android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, f)
                .addToBackStack(null)
                .commit();
    }

    public static void push(FragmentActivity a, Class<? extends Fragment> clazz, Bundle args) {
        try {
            Fragment f = clazz.newInstance();
            f.setArguments(args);
            push(a, f);
        } catch (Exception e) {
            Ui.toast(a, "打开页面失败：" + e.getMessage());
        }
    }

    /** 首页等顶层：清空回退栈后替换 */
    public static void reset(FragmentActivity a, Fragment f) {
        FragmentManager fm = a.getSupportFragmentManager();
        fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fm.beginTransaction()
                .replace(R.id.fragment_container, f)
                .commit();
    }
}