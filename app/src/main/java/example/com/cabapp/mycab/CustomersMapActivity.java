package example.com.cabapp.mycab;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

public class CustomersMapActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener
{

    private GoogleMap mMap;
    GoogleApiClient googleApiClient;
    Location lastLocation;
    LocationRequest locationRequest;

    private Button CustomerLogoutButton;
    private Button CallCabCarButton;
    private String customerID;
    private LatLng CustomerPickUpLocation;
    private int radius = 1;
    private Boolean driverFound = false;
    private String driverFoundID;

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    Marker DriverMarker;
    private DatabaseReference CustomerDatabaseRef;
    private DatabaseReference DriverAvailableRef;
    private DatabaseReference DriversRef;
    private DatabaseReference DriverLocationRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customers_map);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        customerID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        CustomerDatabaseRef = FirebaseDatabase.getInstance().getReference().child("Customers Requests");
        DriverAvailableRef = FirebaseDatabase.getInstance().getReference().child("Drivers Available");
        DriverLocationRef = FirebaseDatabase.getInstance().getReference().child("Drivers Working");

        CustomerLogoutButton = (Button) findViewById(R.id.customer_logout_button);
        CallCabCarButton = (Button) findViewById(R.id.customers_call_cab_button);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        CustomerLogoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
              mAuth.signOut();
              LogoutCustomer();
            }
        });

        CallCabCarButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view)
            {
                GeoFire geoFire = new GeoFire(CustomerDatabaseRef);
                geoFire.setLocation(customerID, new GeoLocation(lastLocation.getLatitude(),lastLocation.getLongitude()));

                CustomerPickUpLocation = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                mMap.addMarker(new MarkerOptions().position(CustomerPickUpLocation).title("Pickup Customer from here"));

                CallCabCarButton.setText("Getting your Driver ....");
                GetClosestDriverCab();
            }
        });

    }

    private void GetClosestDriverCab()
    {
        GeoFire geoFire = new GeoFire(DriverAvailableRef);
        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(CustomerPickUpLocation.latitude, CustomerPickUpLocation.longitude), radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location)
            {
               if (!driverFound)
               {
                   driverFound = true;
                   driverFoundID = key;

                   DriversRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID);
                   HashMap driverMap = new HashMap();
                   driverMap.put("CustomerRideID", customerID);
                   DriversRef.updateChildren(driverMap);

                   GettingDriverLocation();
                   CallCabCarButton.setText("Looking for Driver Location....");
               }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady()
            {
               if (!driverFound)
               {
                   radius = radius + 1;
                   GetClosestDriverCab();
               }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private void GettingDriverLocation()
    {
       DriverLocationRef.child(driverFoundID).child("l")
               .addValueEventListener(new ValueEventListener() {
                   @Override
                   public void onDataChange(DataSnapshot dataSnapshot)
                   {
                      if (dataSnapshot.exists())
                      {
                          List<Object> driverLocationMap = (List<Object>) dataSnapshot.getValue();
                          double LocationLat = 0;
                          double LocationLng = 0;
                          CallCabCarButton.setText("Driver Found");

                          if (driverLocationMap.get(0) != null )
                          {
                              LocationLat = Double.parseDouble(driverLocationMap.get(0).toString());
                          }
                          if (driverLocationMap.get(1) != null )
                          {
                              LocationLng = Double.parseDouble(driverLocationMap.get(1).toString());
                          }

                          LatLng DriverLatLng = new LatLng(LocationLat, LocationLng);
                          if (DriverMarker != null)
                          {
                              DriverMarker.remove();
                          }

                          Location location1 = new Location("");
                          location1.setLatitude(CustomerPickUpLocation.latitude);
                          location1.setLongitude(CustomerPickUpLocation.longitude);

                          Location location2 = new Location("");
                          location2.setLatitude(DriverLatLng.latitude);
                          location2.setLongitude(DriverLatLng.longitude);

                          float Distance = location1.distanceTo(location2);
                          CallCabCarButton.setText("Driver Found:" + String.valueOf(Distance));

                          DriverMarker = mMap.addMarker(new MarkerOptions().position(DriverLatLng).title("Your Driver is here"));
                      }
                   }

                   @Override
                   public void onCancelled(DatabaseError databaseError) {

                   }
               });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle)
    {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(locationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {

            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location)
    {
        lastLocation = location;
        LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(12));
    }

    protected synchronized void buildGoogleApiClient()
    {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        googleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void LogoutCustomer()
    {
        Intent welcomeIntent = new Intent(CustomersMapActivity.this, WelcomeActivity.class);
        welcomeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(welcomeIntent);
        finish();
    }

}
