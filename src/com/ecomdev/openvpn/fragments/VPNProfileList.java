package com.ecomdev.openvpn.fragments;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.ecomdev.openvpn.*;
import com.ecomdev.openvpn.activities.ConfigConverter;
import com.ecomdev.openvpn.activities.DisconnectVPN;
import com.ecomdev.openvpn.activities.FileSelect;
import com.ecomdev.openvpn.activities.VPNPreferences;
import com.ecomdev.openvpn.core.ProfileManager;
import com.ecomdev.openvpn.core.VpnStatus;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Process;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class VPNProfileList extends ListFragment {

	public final static int RESULT_VPN_DELETED = Activity.RESULT_FIRST_USER;
    private static final String TIMER_NAME = "Ping";

	private static final int MENU_ADD_PROFILE = Menu.FIRST;

	private static final int START_VPN_CONFIG = 92;
	private static final int SELECT_PROFILE = 43;
	private static final int IMPORT_PROFILE = 231;
    private static final int FILE_PICKER_RESULT = 392;

	private static final int MENU_IMPORT_PROFILE = Menu.FIRST +1;
    private Map<String, Drawable> mFlags = new HashMap<String, Drawable>(4);
    private Button mPingServers;
    private Button mDisconnect;
    private Timer mTimer;
    private boolean mIsTimerRun = false;
    private ExecutorService mService = Executors.newCachedThreadPool();
    private AtomicInteger mItemsCount = new AtomicInteger();
    private RequestQueue mRequestQueue;
    private ProgressDialog mProgressDialog;


    class VPNArrayAdapter extends ArrayAdapter<VpnProfile> {

		public VPNArrayAdapter(Context context, int resource,
				int textViewResourceId) {
			super(context, resource, textViewResourceId);
		}

		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			View v = super.getView(position, convertView, parent);

			View titleview = v.findViewById(R.id.vpn_list_item_left);
			titleview.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					VpnProfile profile = (VpnProfile) getListAdapter().getItem(position);
					verificationVPN(profile);
				}
			});
            ImageView imageView = (ImageView) v.findViewById(R.id.flag);
            Drawable drawable = mFlags.get(getItem(position).toString());
            imageView.setImageDrawable(drawable);

            if (getItem(position).mPing != -1) {
                TextView pingImage = (TextView) v.findViewById(R.id.pingImage);
                float weight = (getCount() - position) / (float)getCount();
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1f - weight);
                params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.ping_image);
                params.topMargin = getResources().getDimensionPixelSize(R.dimen.ping_image);
                pingImage.setLayoutParams(params);

                View pingImageRightSide = v.findViewById(R.id.pingImageRightSide);
                params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, weight);
                pingImageRightSide.setLayoutParams(params);
                if (position == 0) {
                    pingImage.setText(getString(R.string.fastest_server));
                }
            }

			/*View settingsview = v.findViewById(R.id.quickedit_settings);
			settingsview.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					VpnProfile editProfile = (VpnProfile) getListAdapter().getItem(position);
					editVPN(editProfile);

				}
			});*/

			return v;
		}
	}








	private ArrayAdapter<VpnProfile> mArrayadapter;

	protected VpnProfile mEditProfile=null;



	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);

        mRequestQueue = Volley.newRequestQueue(getActivity());
        mProgressDialog = new ProgressDialog(getActivity());
        if (getPM().getNumberOfProfiles() != 5) // 4 profiles (Canada, USA, USA-2, Europe)
        {
            startConfigImport(Uri.parse("Canada"));
            startConfigImport(Uri.parse("Europe"));
            startConfigImport(Uri.parse("USA"));
            startConfigImport(Uri.parse("USA-2"));
            startConfigImport(Uri.parse("Singapore"));
        }

        mFlags.put("Canada", getResources().getDrawable(R.drawable.canada_flag));
        mFlags.put("Europe", getResources().getDrawable(R.drawable.europe_flag));
        mFlags.put("USA", getResources().getDrawable(R.drawable.usa_flag));
        mFlags.put("USA-2", getResources().getDrawable(R.drawable.usa_flag));
        mFlags.put("Singapore", getResources().getDrawable(R.drawable.singapore_flag));
	}


	class MiniImageGetter implements ImageGetter {


		@Override
		public Drawable getDrawable(String source) {
			Drawable d=null;
			if ("ic_menu_add".equals(source))
				d = getActivity().getResources().getDrawable(android.R.drawable.ic_menu_add);
			else if("ic_menu_archive".equals(source))
				d = getActivity().getResources().getDrawable(R.drawable.ic_menu_archive);
			
			
			
			if(d!=null) {
				d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
				return d;
			}else{
				return null;
			}
		}
	}


	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.vpn_profile_list, container,false);

		TextView newvpntext = (TextView) v.findViewById(R.id.add_new_vpn_hint);
		TextView importvpntext = (TextView) v.findViewById(R.id.import_vpn_hint);
		
		newvpntext.setText(Html.fromHtml(getString(R.string.add_new_vpn_hint),new MiniImageGetter(),null));
		importvpntext.setText(Html.fromHtml(getString(R.string.vpn_import_hint),new MiniImageGetter(),null));

        mPingServers = (Button) v.findViewById(R.id.pingServers);
        mPingServers.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mPingServers.setEnabled(false);
                loadingTextButton();
                checkConnectionSpeedToServer();
            }
        });
        mDisconnect = (Button) v.findViewById(R.id.disconnect);
        mDisconnect.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(),DisconnectVPN.class);
                startActivity(intent);
            }
        });

        VpnStatus.addStateListener(new VpnStatus.StateListener() {
            @Override
            public void updateState(String state, String logmessage, final int localizedResId, VpnStatus.ConnectionStatus level) {
                mPingServers.post(new Runnable() {
                    @Override
                    public void run() {
                        switch (localizedResId) {
                            case R.string.state_noprocess:
                            case R.string.state_disconnected:
                            case R.string.state_user_vpn_password:
                            case R.string.state_nonetwork:
                                mPingServers.setEnabled(true);
                                mDisconnect.setVisibility(View.GONE);
                                break;
                            case R.string.state_connected:
                                mPingServers.setEnabled(false);
                                mDisconnect.setVisibility(View.VISIBLE);
                                break;
                        }
                    }
                });
            }
        });

		return v;

	}

    private void checkConnectionSpeedToServer() {
        mItemsCount.set(0);
        for (int i = 0; i < mArrayadapter.getCount(); i++) {
            final VpnProfile item = mArrayadapter.getItem(i);
            mService.submit(new Runnable() {
                @Override
                public void run() {
                        int minPing = getMinPingTime(item.mServerName, 4);
                        item.mPing = minPing;
                        mItemsCount.incrementAndGet();
                        if (mItemsCount.get() == mArrayadapter.getCount()) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    cancelTimer();
                                    setListAdapterSortByPing();
                                }
                            });
                        }
                }
            });
        }

    }

    private int getMinPingTime(String host, int count) {
        int result = -1;
        try {
            for (int i = 0; i < count; i++) {

                String ping = ping(host);
                if (ping != null) {
                    float pingTime = Float.valueOf(ping);
                    if (result == -1) {
                        result = (int) pingTime;
                    } else {
                        result = (int) Math.min(result, pingTime);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return result;
    }

    private String ping(String host) throws IOException, InterruptedException {
        StringBuffer echo = new StringBuffer();
        Runtime runtime = Runtime.getRuntime();
        Process proc = runtime.exec("ping -c 1 " + host);
        proc.waitFor();
        int exit = proc.exitValue();
        if (exit == 0) {
            InputStreamReader reader = new InputStreamReader(proc.getInputStream());
            BufferedReader buffer = new BufferedReader(reader);
            String line = "";
            while ((line = buffer.readLine()) != null) {
                echo.append(line + "\n");
            }
            return getPingStats(echo.toString());
        } else if (exit == 1) {
            return null;
        } else {
            return null;
        }
    }

    public static String getPingStats(String s) {
        if (s.contains("0% packet loss")) {
            int start = s.indexOf("/mdev = ");
            int end = s.indexOf(" ms\n", start);
            s = s.substring(start + 8, end);
            String stats[] = s.split("/");
            return stats[2];
        } else if (s.contains("100% packet loss")) {
            return null;
        } else if (s.contains("% packet loss")) {
            return null;
        } else if (s.contains("unknown host")) {
            return null;
        } else {
            return null;
        }
    }

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setListAdapter();
	}

    static class VpnProfileNameComparator implements Comparator<VpnProfile> {

        @Override
        public int compare(VpnProfile lhs, VpnProfile rhs) {
            if (lhs == rhs)
                // Catches also both null
                return 0;

            if (lhs == null)
                return -1;
            if (rhs == null)
                return 1;

            if (lhs.mName == null)
                return -1;
            if (rhs.mName == null)
                return 1;

            return lhs.mName.compareTo(rhs.mName);
        }

    }

    private class VPNProfilePingComparator implements Comparator<VpnProfile> {

        @Override
        public int compare(VpnProfile vpnProfile, VpnProfile vpnProfile2) {
            if (vpnProfile == null) {
                return 1;
            }

            if (vpnProfile2 == null) {
                return -1;
            }

            if (vpnProfile.mPing != -1 && vpnProfile2.mPing != -1) {
                if (vpnProfile.mPing == vpnProfile2.mPing) {
                    return compareName(vpnProfile, vpnProfile2);
                } else {
                    return Integer.valueOf(vpnProfile.mPing).compareTo(Integer.valueOf(vpnProfile2.mPing));
                }

            }

            if (vpnProfile.mPing == -1 && vpnProfile2.mPing == -1) {
                return compareName(vpnProfile, vpnProfile2);
            }

            if (vpnProfile.mPing == -1) {
                return 1;
            }

            if (vpnProfile2.mPing == -1) {
                return -1;
            }

            return 0;
        }

        private int compareName(VpnProfile vpnProfile, VpnProfile vpnProfile2) {
            if (vpnProfile.mName == null)
                return -1;
            if (vpnProfile2.mName == null)
                return 1;

            return vpnProfile.mName.compareToIgnoreCase(vpnProfile2.mName);
        }
    }

    private void setListAdapterSortByPing() {
        if (mArrayadapter != null) {
            Collection<VpnProfile> allvpn = getPM().getProfiles();

            TreeSet<VpnProfile> sortedset = new TreeSet<VpnProfile>(new VPNProfilePingComparator());
            sortedset.addAll(allvpn);

            mArrayadapter.clear();
            mArrayadapter.addAll(sortedset);

            setListAdapter(mArrayadapter);
            mArrayadapter.notifyDataSetChanged();
            mPingServers.setEnabled(true);
            mPingServers.setText(getText(R.string.ping_servers));
        }
    }

    private void loadingTextButton() {
        mTimer = new Timer(TIMER_NAME);
        mIsTimerRun = true;
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                int count = 4;
                while (mIsTimerRun) {
                    final StringBuffer stringBuffer = new StringBuffer("Looking for the fastest server for you");
                    for (int i = 0; i < count && mIsTimerRun; i++) {
                        mPingServers.post(new Runnable() {
                            @Override
                            public void run() {
                                mPingServers.setText(stringBuffer);
                            }
                        });

                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        stringBuffer.append(".");
                    }
                }

            }
        }, 0);
    }

    private void cancelTimer() {
        mIsTimerRun = false;
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

	private void setListAdapter() {
		mArrayadapter = new VPNArrayAdapter(getActivity(),R.layout.vpn_list_item,R.id.vpn_item_title);
		Collection<VpnProfile> allvpn = getPM().getProfiles();

        TreeSet<VpnProfile> sortedset = new TreeSet<VpnProfile>(new VPNProfilePingComparator());
        sortedset.addAll(allvpn);
        mArrayadapter.addAll(sortedset);

        setListAdapter(mArrayadapter);
	}



/*	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		menu.add(0, MENU_ADD_PROFILE, 0 , R.string.menu_add_profile)
		.setIcon(android.R.drawable.ic_menu_add)
		.setAlphabeticShortcut('a')
		.setTitleCondensed(getActivity().getString(R.string.add))
		.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS |  MenuItem.SHOW_AS_ACTION_WITH_TEXT);

		menu.add(0, MENU_IMPORT_PROFILE, 0,  R.string.menu_import)
		.setIcon(R.drawable.ic_menu_archive)
		.setAlphabeticShortcut('i')
		.setTitleCondensed(getActivity().getString(R.string.menu_import_short))
		.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT );
	}*/


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final int itemId = item.getItemId();
		if (itemId == MENU_ADD_PROFILE) {
			onAddProfileClicked();
			return true;
		} else if (itemId == MENU_IMPORT_PROFILE) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                startFilePicker();
            else
			    startImportConfig();

			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void startFilePicker() {
       Intent i = Utils.getFilePickerIntent(Utils.FileType.OVPN_CONFIG);
       startActivityForResult(i, FILE_PICKER_RESULT);
    }

    private void startImportConfig() {
		Intent intent = new Intent(getActivity(),FileSelect.class);
		intent.putExtra(FileSelect.NO_INLINE_SELECTION, true);
		intent.putExtra(FileSelect.WINDOW_TITLE, R.string.import_configuration_file);
		startActivityForResult(intent, SELECT_PROFILE);
	}





	private void onAddProfileClicked() {
		Context context = getActivity();
		if (context != null) {
			final EditText entry = new EditText(context);
			entry.setSingleLine();

			AlertDialog.Builder dialog = new AlertDialog.Builder(context);
			dialog.setTitle(R.string.menu_add_profile);
			dialog.setMessage(R.string.add_profile_name_prompt);
			dialog.setView(entry);


			dialog.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String name = entry.getText().toString();
					if (getPM().getProfileByName(name)==null) {
						VpnProfile profile = new VpnProfile(name);
						addProfile(profile);
						editVPN(profile);
					} else {
						Toast.makeText(getActivity(), R.string.duplicate_profile_name, Toast.LENGTH_LONG).show();
					}
				}


			});
			dialog.setNegativeButton(android.R.string.cancel, null);
			dialog.create().show();
		}

	}

	private void addProfile(VpnProfile profile) {
		getPM().addProfile(profile);
		getPM().saveProfileList(getActivity());
		getPM().saveProfile(getActivity(),profile);
		mArrayadapter.add(profile);
	}

	private ProfileManager getPM() {
		return ProfileManager.getInstance(getActivity());
	}


	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if(resultCode == RESULT_VPN_DELETED){
			if(mArrayadapter != null && mEditProfile !=null)
				mArrayadapter.remove(mEditProfile);
		}

		if(resultCode != Activity.RESULT_OK)
			return;

		if (requestCode == START_VPN_CONFIG) {
			String configuredVPN = data.getStringExtra(VpnProfile.EXTRA_PROFILEUUID);

			VpnProfile profile = ProfileManager.get(getActivity(),configuredVPN);
			getPM().saveProfile(getActivity(), profile);
			// Name could be modified, reset List adapter
			setListAdapter();

		} else if(requestCode== SELECT_PROFILE) {
            String fileData = data.getStringExtra(FileSelect.RESULT_DATA);
            Uri uri = new Uri.Builder().path(fileData).scheme("file").build();

            startConfigImport(uri);
		} else if(requestCode == IMPORT_PROFILE) {
			String profileUUID = data.getStringExtra(VpnProfile.EXTRA_PROFILEUUID);
			mArrayadapter.add(ProfileManager.get(getActivity(), profileUUID));
		} else if(requestCode == FILE_PICKER_RESULT) {
            if (data != null) {
                Uri uri = data.getData();
                startConfigImport(uri);
            }
        }

	}

    private void startConfigImport(Uri uri) {
        Intent startImport = new Intent(getActivity(),ConfigConverter.class);
        startImport.setAction(ConfigConverter.IMPORT_PROFILE);
        startImport.setData(uri);
        startActivityForResult(startImport, IMPORT_PROFILE);
    }


    private void editVPN(VpnProfile profile) {
		mEditProfile =profile;
		Intent vprefintent = new Intent(getActivity(),VPNPreferences.class)
		.putExtra(getActivity().getPackageName() + ".profileUUID", profile.getUUID().toString());

		startActivityForResult(vprefintent,START_VPN_CONFIG);
	}

	private void verificationVPN(final VpnProfile profile) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean needToVerification = prefs.getBoolean(Constants.PREF_NEED_TO_VERIFICATION, true);
        if (needToVerification) {
            mProgressDialog.show();
            TelephonyManager telephonyManager = (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
            String deviceId = telephonyManager.getDeviceId();
            String url = "http://happymom.info/spider/check.php";
            Uri.Builder builder = Uri.parse(url).buildUpon();
            builder.appendQueryParameter("imei", deviceId);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, builder.toString(), null, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject jsonObject) {
                    parseResponse(jsonObject, profile);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError volleyError) {
                    checkingError();
                }
            });

            request.setRetryPolicy(new DefaultRetryPolicy(DefaultRetryPolicy.DEFAULT_TIMEOUT_MS, 5, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            mRequestQueue.add(request);
            mRequestQueue.start();
        } else {
            startVPN(profile);
        }
	}

    private void parseResponse(JSONObject jsonResponse, VpnProfile profile) {
        try {
            int status = jsonResponse.getInt("status");
            boolean isDemo = status == 1 ? true : false;
            int leftTime = jsonResponse.getInt("left") * 1000;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(Constants.PREF_NEED_TO_VERIFICATION, false);
            editor.putBoolean(Constants.PREF_IS_DEMO, true);
            editor.putBoolean(Constants.PREF_IS_TIMEOUT, !isDemo);
            editor.putInt(Constants.PREF_LEFT_TIME, leftTime);
            int timeToStart = leftTime % Constants.ONE_HOUR;
            int leftHours = (leftTime - timeToStart) / Constants.ONE_HOUR + 1;
            editor.putInt(Constants.PREF_LEFT_HOURS, leftHours);
            editor.commit();

            mProgressDialog.cancel();
            startVPN(profile);
        } catch (JSONException e) {
            checkingError();
            e.printStackTrace();
        }
    }

    private void startVPN (VpnProfile profile) {
        getPM().saveProfile(getActivity(), profile);
        Intent intent = new Intent(getActivity(),LaunchVPN.class);
        intent.putExtra(LaunchVPN.EXTRA_KEY, profile.getUUID().toString());
        intent.setAction(Intent.ACTION_MAIN);
        startActivity(intent);
    }

    private void checkingError() {
        mProgressDialog.cancel();
        Toast.makeText(getActivity(), getString(R.string.error_connection_with_server), Toast.LENGTH_LONG).show();
    }

}
