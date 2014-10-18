package com.ecomdev.openvpn.activities;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import com.ecomdev.openvpn.Constants;
import com.ecomdev.openvpn.DemoService;
import com.ecomdev.openvpn.R;
import com.ecomdev.openvpn.core.OpenVpnService;
import com.ecomdev.openvpn.core.ProfileManager;
import com.ecomdev.openvpn.fragments.*;


public class MainActivity extends Activity implements DemoService.UpdateTimeListener {

    private MenuItem mDemoTimeMenuItem;
    protected OpenVpnService mService;

    private ServiceConnection mConnection = new ServiceConnection() {


        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            OpenVpnService.LocalBinder binder = (OpenVpnService.LocalBinder) service;
            mService = binder.getService();

            ProfileManager.setConntectedVpnProfileDisconnected(MainActivity.this);
            if (mService != null && mService.getManagement() != null) {
                mService.getManagement().stopVPN();
            }

            unbindService(mConnection);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService =null;
        }

    };

	protected void onCreate(android.os.Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ActionBar bar = getActionBar();
		bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        Tab vpnListTab = bar.newTab().setText(R.string.vpn_list_title);
		//Tab generalTab = bar.newTab().setText(R.string.generalsettings);
		//Tab faqtab = bar.newTab().setText(R.string.faq);
		Tab abouttab = bar.newTab().setText(R.string.about);

		vpnListTab.setTabListener(new TabListener<VPNProfileList>("profiles", VPNProfileList.class));
		//generalTab.setTabListener(new TabListener<GeneralSettings>("settings", GeneralSettings.class));
		//faqtab.setTabListener(new TabListener<FaqFragment>("faq", FaqFragment.class));
		abouttab.setTabListener(new TabListener<AboutFragment>("about", AboutFragment.class));

		bar.addTab(vpnListTab);
		//bar.addTab(generalTab);
		//bar.addTab(faqtab);
		bar.addTab(abouttab);

        if (false) {
            Tab logtab = bar.newTab().setText("Log");
            logtab.setTabListener(new TabListener<LogFragment>("log", LogFragment.class));
            bar.addTab(logtab);
        }

        if(SendDumpFragment.getLastestDump(this)!=null) {
			Tab sendDump = bar.newTab().setText(R.string.crashdump);
			sendDump.setTabListener(new TabListener<SendDumpFragment>("crashdump",SendDumpFragment.class));
			bar.addTab(sendDump);
		}

        startDemoService();
	}

    private void startDemoService() {

    }

    @Override
    public void updateDemoHours() {
        View actionView = mDemoTimeMenuItem.getActionView();
        TextView timeView = (TextView) actionView.findViewById(R.id.demoTime);
        SharedPreferences preferences = getSharedPreferences(Constants.sMAIN_SHARED_PREFERENCE, MODE_PRIVATE);
        int defaultTime = getResources().getInteger(R.integer.demoHours);
        timeView.setText(preferences.getInt(Constants.sPREF_LEFT_HOURS, defaultTime) + "h");
    }

    @Override
    public void timeOut() {
        Intent intent = new Intent(this, OpenVpnService.class);
        intent.setAction(OpenVpnService.START_SERVICE);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    protected class TabListener<T extends Fragment> implements ActionBar.TabListener
	{
		private Fragment mFragment;
		private String mTag;
		private Class<T> mClass;

        public TabListener(String tag, Class<T> clz) {
            mTag = tag;
            mClass = clz;

            // Check to see if we already have a fragment for this tab, probably
            // from a previously saved state.  If so, deactivate it, because our
            // initial state is that a tab isn't shown.
            mFragment = getFragmentManager().findFragmentByTag(mTag);
            if (mFragment != null && !mFragment.isDetached()) {
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ft.detach(mFragment);
                ft.commit();
            }
        }
      
        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            if (mFragment == null) {
                mFragment = Fragment.instantiate(MainActivity.this, mClass.getName());
                ft.add(android.R.id.content, mFragment, mTag);
            } else {
                ft.attach(mFragment);
            }
        }

        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
            if (mFragment != null) {
                ft.detach(mFragment);
            }
        }


		@Override
		public void onTabReselected(Tab tab, FragmentTransaction ft) {

		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		System.out.println(data);


	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.demo_menu, menu);

        mDemoTimeMenuItem = menu.findItem(R.id.menuDemoTime);

        return super.onCreateOptionsMenu(menu);
    }
}
