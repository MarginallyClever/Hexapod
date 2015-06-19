package Hexapod;

import java.nio.FloatBuffer;
import java.nio.ByteBuffer;
import java.util.prefs.Preferences;

import javax.vecmath.Vector3f;
import javax.media.opengl.GL2;


public class Spidee 
extends RobotWithSerialConnection {
	private Preferences prefs = Preferences.userRoot().node("Spidee");
	
	//math public static finalants
	public static final float RAD2DEG = 180.0f/(float)Math.PI;
	public static final float DEG2RAD = (float)Math.PI/180.0f;
	
	public static final float MAX_VEL = 60.0f/(1000.0f*0.2f);   // 60 degrees/0.2s for TowerPro SG-5010.
	public static final float MAX_VEL_TRANSLATED = 512.0f*MAX_VEL/360.0f;

	// must match defines in arduino code =see v7.pde;
	public static final int BUTTONS_X_POS =0;
	public static final int BUTTONS_X_NEG =1;
	public static final int BUTTONS_Y_POS =2;
	public static final int BUTTONS_Y_NEG =3;
	public static final int BUTTONS_Z_POS =4;
	public static final int BUTTONS_Z_NEG =5;
	public static final int BUTTONS_X_ROT_POS =6;
	public static final int BUTTONS_X_ROT_NEG =7;
	public static final int BUTTONS_Y_ROT_POS =8;
	public static final int BUTTONS_Y_ROT_NEG =9;
	public static final int BUTTONS_Z_ROT_POS =10;
	public static final int BUTTONS_Z_ROT_NEG =11;

	public static final int BUTTONS_0 =12;  // reset
	public static final int BUTTONS_1 =13;  // d-pad up?
	public static final int BUTTONS_2 =14;  // d-pad down?
	public static final int BUTTONS_3 =15;  // d-pad left?
	public static final int BUTTONS_4 =16;  // d-pad right?
	public static final int BUTTONS_5 =17;  // square
	public static final int BUTTONS_6 =18;  // circle
	public static final int BUTTONS_7 =19;  // x
	public static final int BUTTONS_8 =20;  // triangle

	public static final int BUTTONS_MAX =21;

	public static enum MoveModes {
	  MOVE_MODE_CALIBRATE,
	
	  MOVE_MODE_SITDOWN,
	  MOVE_MODE_STANDUP,
	  MOVE_MODE_BODY,
	  MOVE_MODE_RIPPLE,
	  MOVE_MODE_WAVE,
	  MOVE_MODE_TRIPOD,
	
	  MOVE_MODE_MAX
	};
	

	  String name;

	  Location body = new Location();
	  Location target = new Location();
	  Leg [] legs = new Leg[6];

	  int [] buttons = new int[BUTTONS_MAX];
	  
	  float body_radius;
	  float standing_radius;  // Distance from center of body to foot on ground in body-relative XY plane.  Used for motion planning
	  float standing_height;  // How high the body should "ride" when walking
	  float turn_stride_length;  // how far to move a foot when turning
	  float stride_length;  // how far to move a step when walking
	  float stride_height;  // how far to lift a foot
	  float max_leg_length;

	  MoveModes move_mode;  // What is the bot's current agenda?
	  float gait_cycle;

	  float speed_scale;
	  boolean paused;
	  boolean initialized = false;
	  boolean render_meshes = true;

	  // models
	  // @TODO: load each model only once.
	  Model model_thigh = new Model();
	  Model model_body = new Model();
	  Model model_shoulder_left = new Model();
	  Model model_shoulder_right = new Model();
	  Model model_shin_left = new Model();
	  Model model_shin_right = new Model();
	  
	  
	
	public Spidee(String name) {
		super(name);

		int i;
		for(i=0;i<6;++i) {
			legs[i] = new Leg();
		}
		
		body.pos.set(0,0,0);
		body.up.set(0,0,1);
		body.forward.set(0,1,0);
		body.left.set(-1,0,0);

		body_radius=10;
		legs[0].facing_angle=45;
		legs[1].facing_angle=90;
		legs[2].facing_angle=135;
		legs[3].facing_angle=-135;
		legs[4].facing_angle=-90;
		legs[5].facing_angle=-45;
		  
		legs[0].name="RF";
		legs[1].name="RM";
		legs[2].name="RB";
		legs[3].name="LB";
		legs[4].name="LM";
		legs[5].name="LF";

		target.forward.set(body.pos);
		target.forward.add(new Vector3f(0,1,0));
		  
		target.up.set(body.pos);
		target.up.add(new Vector3f(0,0,1));

		move_mode = MoveModes.MOVE_MODE_CALIBRATE;
		speed_scale = 1.0f;

		Preferences pn;
		  
		int j=0;
		for(i=0;i<7;++i) {
		    if(i==3) continue;
		    Leg leg = legs[j++];
		    
		    leg.active=false;

		    float x = (i+1)*(float)Math.PI*2.0f/8.0f;
		    //float y = leg.facing_angle*DEG2RAD;
		    leg.pan_joint.forward.set((float)Math.sin(x),(float)Math.cos(x),0);
		    leg.pan_joint.forward.normalize();
		    leg.pan_joint.up.set( body.up );
		    
		    leg.pan_joint.left.cross( leg.pan_joint.forward,leg.pan_joint.up );
		    leg.pan_joint.angle = 127.0f;
		    leg.pan_joint.last_angle = (int)leg.pan_joint.angle;

		    leg.tilt_joint.forward.set( leg.pan_joint.forward );
		    leg.tilt_joint.up.set( leg.pan_joint.up );
		    leg.tilt_joint.left.set( leg.pan_joint.left );
		    leg.tilt_joint.angle = leg.pan_joint.angle;
		    leg.tilt_joint.last_angle = leg.pan_joint.last_angle;

		    leg.knee_joint.forward.set( leg.pan_joint.forward );
		    leg.knee_joint.up.set( leg.pan_joint.up );
		    leg.knee_joint.left.set( leg.pan_joint.left );
		    leg.knee_joint.angle = leg.pan_joint.angle;
		    leg.knee_joint.last_angle = leg.pan_joint.last_angle;

		    leg.ankle_joint.forward.set( leg.pan_joint.forward );
		    leg.ankle_joint.up.set( leg.pan_joint.up );
		    leg.ankle_joint.left.set( leg.pan_joint.left );
		    leg.ankle_joint.angle = leg.pan_joint.angle;
		    leg.ankle_joint.last_angle = leg.pan_joint.last_angle;

		    leg.pan_joint.relative.set(leg.pan_joint.forward);
		    leg.pan_joint.relative.scale(body_radius);
		    leg.pan_joint.relative.z += 2.0f;
		    leg.tilt_joint.relative.set(leg.pan_joint.forward);
		    leg.tilt_joint.relative.scale(2.232f);
		    Vector3f a = new Vector3f(leg.pan_joint.forward);
		    Vector3f b = new Vector3f(leg.pan_joint.up);
		    a.scale(5.5f);
		    b.scale(5.5f);
		    leg.knee_joint.relative.set(a);
		    leg.knee_joint.relative.add(b);
		    leg.ankle_joint.relative.set(leg.pan_joint.forward);
		    leg.ankle_joint.relative.scale(10.0f);

		    leg.pan_joint.pos.set(body.pos);
		    leg.pan_joint.pos.add(leg.pan_joint.relative);
		    
		    leg.tilt_joint.pos.set(leg.pan_joint.pos);
		    leg.tilt_joint.pos.add(leg.tilt_joint.relative);
		    leg.knee_joint.pos.set(leg.tilt_joint.pos);
		    leg.knee_joint.pos.add(leg.knee_joint.relative);
		    leg.ankle_joint.pos.set(leg.knee_joint.pos);
		    leg.ankle_joint.pos.add(leg.ankle_joint.relative);


		    pn = prefs.node("Leg "+j);
		    leg.pan_joint.angle_max  = pn.getInt("pan_max",127+60);
		    leg.pan_joint.angle_min  = pn.getInt("pan_min",127-60);
		    leg.tilt_joint.angle_max = pn.getInt("tilt_max",240);
		    leg.tilt_joint.angle_min = pn.getInt("tilt_min",15);
		    leg.knee_joint.angle_max = pn.getInt("knee_max",240);
		    leg.knee_joint.angle_min = pn.getInt("knee_min",15);

		    leg.pan_joint.zero  = pn.getFloat("pan_zero", 127);
		    leg.tilt_joint.zero = pn.getFloat("tilt_zero",127);
		    leg.knee_joint.zero = pn.getFloat("knee_zero",127);
		    leg.pan_joint.scale  = pn.getFloat("pan_scale", 1);
		    leg.tilt_joint.scale = pn.getFloat("tilt_scale",1);
		    leg.knee_joint.scale = pn.getFloat("knee_scale",1);
		}

		max_leg_length = legs[0].knee_joint.relative.length()
						+ legs[0].ankle_joint.relative.length();

		// set the servo addresses in case a pin doesn't work on the ServoBoard.
		pn = prefs.node("Leg 0");
		legs[0].pan_joint .servo_address = pn.getInt("pan_address",  0);
		legs[0].tilt_joint.servo_address = pn.getInt("tilt_address", 1);
		legs[0].knee_joint.servo_address = pn.getInt("knee_address", 2);
		pn = pn.node("Leg 1");
		legs[1].pan_joint .servo_address = pn.getInt("pan_address",  3);
		legs[1].tilt_joint.servo_address = pn.getInt("tilt_address", 4);
		legs[1].knee_joint.servo_address = pn.getInt("knee_address", 5);
		pn = pn.node("Leg 2");
		legs[2].pan_joint .servo_address = pn.getInt("pan_address",  6);
		legs[2].tilt_joint.servo_address = pn.getInt("tilt_address", 7);
		legs[2].knee_joint.servo_address = pn.getInt("knee_address", 8);
		pn = pn.node("Leg 3");
		legs[3].pan_joint .servo_address = pn.getInt("pan_address", 22);
		legs[3].tilt_joint.servo_address = pn.getInt("tilt_address",23);
		legs[3].knee_joint.servo_address = pn.getInt("knee_address",24);
		pn = pn.node("Leg 4");
		legs[4].pan_joint .servo_address = pn.getInt("pan_address", 19);
		legs[4].tilt_joint.servo_address = pn.getInt("tilt_address",20);
		legs[4].knee_joint.servo_address = pn.getInt("knee_address",21);
		pn = pn.node("Leg 5");
		legs[5].pan_joint .servo_address = pn.getInt("pan_address", 16);
		legs[5].tilt_joint.servo_address = pn.getInt("tilt_address",17);
		legs[5].knee_joint.servo_address = pn.getInt("knee_address",18);
		//*
		// keys
		try {
			prefs.removeNode();
		}
		catch(Exception e) {}
		prefs = Preferences.userRoot().node("Spidee");
		//*/
		// read in the control keys
		Input.GetSingleton().AddContext("spidee","strafe_left"   , prefs.get("strafe_left"   ,"VK_A"));
		Input.GetSingleton().AddContext("spidee","strafe_right"  , prefs.get("strafe_right"  ,"VK_D"));
		Input.GetSingleton().AddContext("spidee","strafe_forward", prefs.get("strafe_forward","VK_W"));
		Input.GetSingleton().AddContext("spidee","strafe_back"   , prefs.get("strafe_back"   ,"VK_S"));
		Input.GetSingleton().AddContext("spidee","raise_body"    , prefs.get("raise_body"    ,"VK_Q"));
		Input.GetSingleton().AddContext("spidee","lower_body"    , prefs.get("lower_body"    ,"VK_E"));

		Input.GetSingleton().AddContext("spidee","turn_left" , prefs.get("turn_left" ,"VK_Z"));
		Input.GetSingleton().AddContext("spidee","turn_right", prefs.get("turn_right","VK_X"));
		Input.GetSingleton().AddContext("spidee","tilt_up"   , prefs.get("tilt_up"   ,"VK_R"));
		Input.GetSingleton().AddContext("spidee","tilt_down" , prefs.get("tilt_down" ,"VK_F"));
		Input.GetSingleton().AddContext("spidee","tilt_left" , prefs.get("tilt_left" ,"VK_C"));
		Input.GetSingleton().AddContext("spidee","tilt_right", prefs.get("tilt_right","VK_V"));

		Input.GetSingleton().AddContext("spidee","widen_stance"  , prefs.get("widen_stance"  ,"VK_Y"));
		Input.GetSingleton().AddContext("spidee","narrow_stance" , prefs.get("narrow_stance" ,"VK_H"));
		Input.GetSingleton().AddContext("spidee","knee_up"   , prefs.get("knee_up"   ,"VK_D"));
		Input.GetSingleton().AddContext("spidee","knee_down" , prefs.get("knee_down" ,"VK_F"));

		Input.GetSingleton().AddContext("spidee","recenter"  , prefs.get("recenter"  ,"VK_B"));
		Input.GetSingleton().AddContext("spidee","reset_legs", prefs.get("reset_legs","VK_L"));

		Input.GetSingleton().AddContext("spidee","change_mode_next", prefs.get("change_mode_next","VK_COMMA" ));
		Input.GetSingleton().AddContext("spidee","change_mode_prev", prefs.get("change_mode_prev","VK_PERIOD"));

		Input.GetSingleton().AddContext("spidee","calibrate" , prefs.get("calibrate" ,"VK_F9"));
		Input.GetSingleton().AddContext("spidee","connect", prefs.get("connect" ,"VK_F3"));
		
		Input.GetSingleton().AddContext("spidee","pause",      prefs.get("pause","VK_SPACE"));
		Input.GetSingleton().AddContext("spidee","speed_up",   prefs.get("speed_up","VK_PLUS"));
		Input.GetSingleton().AddContext("spidee","slow_down" , prefs.get("slow_down" ,"VK_MINUS"));
		Input.GetSingleton().AddContext("spidee","one_frame" , prefs.get("one_frame" ,"VK_CONTROL"));
		
		Input.GetSingleton().AddContext("spidee","leg1", prefs.get("leg1" ,"VK_1"));
		Input.GetSingleton().AddContext("spidee","leg2", prefs.get("leg2" ,"VK_2"));
		Input.GetSingleton().AddContext("spidee","leg3", prefs.get("leg3" ,"VK_3"));
		Input.GetSingleton().AddContext("spidee","leg4", prefs.get("leg4" ,"VK_4"));
		Input.GetSingleton().AddContext("spidee","leg5", prefs.get("leg5" ,"VK_5"));
		Input.GetSingleton().AddContext("spidee","leg6", prefs.get("leg6" ,"VK_6"));
		
		
		// posture settings?
		stride_length = prefs.getFloat( "stride_length", 15.0f );
		stride_height = prefs.getFloat( "stride_height", 5.0f );
		turn_stride_length = prefs.getFloat( "turn_stride_length", 150.0f );

		standing_radius = prefs.getFloat( "standing_radius", 21.0f );
		standing_height = prefs.getFloat( "standing_height", 5.5f );
		  
		paused=false;
	}
	
	void Init(GL2 gl2) {
		model_thigh.Load(gl2, "thigh.stl");
		model_body.Load(gl2, "body.stl");
		model_shoulder_left.Load(gl2, "shoulder_left.stl");
		model_shoulder_right.Load(gl2, "shoulder_right.stl");
		model_shin_left.Load(gl2, "shin_left.stl");
		model_shin_right.Load(gl2, "shin_right.stl");
		initialized=true;
	}
	
	
	
	void PlantFeet() {
		  int i;
		  for(i=0;i<6;++i) {
		    legs[i].ankle_joint.pos.z = 0;
		  }
	}
	
	
	void Center_Body_Around_Feet(float dt) {
		  // center the body around the feet
		  Vector3f p = new Vector3f(0,0,0);
		  Vector3f r = new Vector3f(0,0,0);
		  int i;
		  for(i=0;i<6;++i) {
		    if(legs[i].ankle_joint.pos.z<=0) {
		      p.add(legs[i].ankle_joint.pos);
		    } else {
		      p.add(legs[i].ankle_joint.pos);
		    }
		    if(i<3) r.sub(legs[i].ankle_joint.pos);
		    else    r.add(legs[i].ankle_joint.pos);
		  }

		  p.scale(1.0f/6.0f);
		  r.scale(1.0f/6.0f);
		  Vector3f dp = new Vector3f( p.x - body.pos.x, p.y-body.pos.y,0 );
		  dp.scale(0.5f);
		  body.pos.add(dp);
		  // zero body height
		  body.pos.z += ( standing_height - body.pos.z ) * dt;

		  // zero the body orientation
		  target.left = r;
		  target.left.normalize();
		  target.up.set(0,0,1);
		  target.forward.cross(target.left, target.up);
	}
	
	void Translate_Body(float dt) {
	  // IK test - moving body

	  if(Input.GetSingleton().GetButtonState("camera","active")==Input.ButtonState.OFF) {
	    float a=(float)buttons[BUTTONS_X_NEG]
	           -(float)buttons[BUTTONS_X_POS];
	    float b=(float)buttons[BUTTONS_Y_NEG]
	           -(float)buttons[BUTTONS_Y_POS];
	    float c=(float)buttons[BUTTONS_Z_NEG]
	           -(float)buttons[BUTTONS_Z_POS];
	    float a1=Math.max(Math.min(a,MAX_VEL),-MAX_VEL);  // sideways
	    float b1=Math.max(Math.min(b,MAX_VEL),-MAX_VEL);  // forward/back
	    float c1=Math.max(Math.min(c,MAX_VEL),-MAX_VEL);  // raise/lower body

	    
	    Vector3f forward = new Vector3f( body.forward );
	    forward.scale(b1);
	    Vector3f t2 = new Vector3f(body.left);
	    t2.scale(a1);
	    forward.sub(t2);
	    
	    body.pos.z-=c1;
	    if(body.pos.z>0) {
	      body.pos.add( forward );
	      int i;
	      for(i=0;i<6;++i) {
	        legs[i].pan_joint.pos.add( forward );
	        legs[i].pan_joint.pos.z += c1;
	      }
	    }
	  }
	}
	
	void Translate_Body_Towards(Vector3f point,float dt) {
	  Vector3f dp = new Vector3f(point);
	  dp.sub( body.pos );
	  
	  float dpl = dp.length();
	  if( dpl > dt ) {
	    dp.normalize();
	    dp.scale( dt );
	    if( body.pos.z != 0 || dp.z >= 0 ) {
	      body.pos.add(dp);
	      int i;
	      for( i = 0; i < 6; ++i ) {
	        legs[i].pan_joint.pos.add( dp );
	      }
	    }
	  } else {
	    body.pos = point;
	  }
	}
	
	void Angle_Body(float dt) {
		// IK test - moving body

		if(Input.GetSingleton().GetButtonState("camera","active")==Input.ButtonState.OFF) {
		    float a = buttons[BUTTONS_X_ROT_NEG] - buttons[BUTTONS_X_ROT_POS];
		    float b = buttons[BUTTONS_Y_ROT_NEG] - buttons[BUTTONS_Y_ROT_POS];
		    float c = buttons[BUTTONS_Z_ROT_NEG] - buttons[BUTTONS_Z_ROT_POS];
		    float a1=Math.max(Math.min(a,MAX_VEL),-MAX_VEL);
		    float b1=Math.max(Math.min(b,MAX_VEL),-MAX_VEL);
		    float c1=Math.max(Math.min(c,MAX_VEL),-MAX_VEL);
		    //System.out.println(""+a1+"\t"+b1+"\t"+c1);

		    if( a1 !=0 || b1 != 0 ) {
			    Vector3f forward=new Vector3f( body.forward );
			    forward.scale( a1 );
			    Vector3f sideways=new Vector3f( body.left );
			    sideways.scale( -b1 );
			    forward.add( sideways );
			    forward.normalize();
			    forward.scale(turn_stride_length * DEG2RAD * dt / 3.0f);
			    target.up.add( forward );
			    target.up.normalize();
		    }
		    
		    if( c1 != 0 ) {
		    	Vector3f left = new Vector3f( target.left );
		    	left.scale(c1 * turn_stride_length * DEG2RAD * dt / 6.0f);
		    	target.forward.add( left );
		    	target.forward.normalize();
		    	target.left.cross(target.up, target.forward);
		    }
		}
	}
	
	void Move_Body(float dt) {
	  Translate_Body(dt);
	  Angle_Body(dt);

	  if(buttons[BUTTONS_0]>0) {
	    Center_Body_Around_Feet(dt);
	  }
	}
	
	void Stand_Up(float dt) {
	  int i;
	  int onfloor = 0;
	  float scale = 2.0f;

	  // touch the feet to the floor
	  for(i=0;i<6;++i) {
	    if(legs[i].ankle_joint.pos.z>0) legs[i].ankle_joint.pos.z-=4*scale*dt;
	    else ++onfloor;

	    // contract - put feet closer to shoulders
	    Vector3f df = new Vector3f(legs[i].ankle_joint.pos);
	    df.sub(body.pos);
	    df.z=0;
	    if(df.length()>standing_radius) {
	      df.normalize();
	      df.scale(6*scale*dt);
	      legs[i].ankle_joint.pos.sub(df);
	    }
	  }

	  if(onfloor==6) {
	    // we've planted all feet, raise the body a bit
	    if( body.pos.z < standing_height ) body.pos.z+=2*scale*dt;

	    for(i=0;i<6;++i) {
	      Vector3f ds = new Vector3f( legs[i].pan_joint.pos );
	      ds.sub( body.pos );
	      ds.normalize();
	      ds.scale(standing_radius);
	      legs[i].npoc.set(body.pos.x+ds.x,
	    		  			body.pos.y+ds.y,
	    		  			0);
	    }
	  }
	}

	boolean Sit_Down(float dt) {
	  int i;
	  int legup=0;
	  float scale=1.0f;

	  // we've planted all feet, lower the body to the ground
	  if( body.pos.z > 0 ) body.pos.z -= 2 * scale * dt;
	  else {
	    for( i = 0; i < 6; ++i ) {

	      // raise feet
	      Vector3f ls = new Vector3f( legs[i].ankle_joint.pos );
	      ls.sub( legs[i].pan_joint.pos );
	      if( ls.length() < 16 ) {
	        ls.z=0;
	        ls.normalize();
	        ls.scale( 4 * scale * dt );
	        legs[i].ankle_joint.pos.add( ls );
	      } else ++legup;

	      if( legs[i].ankle_joint.pos.z-legs[i].pan_joint.pos.z < 5.5 ) legs[i].ankle_joint.pos.z += 4 * scale * dt;
	      else ++legup;

	      if( legs[i].knee_joint.pos.z-legs[i].pan_joint.pos.z < 5.5 ) legs[i].knee_joint.pos.z += 4 * scale * dt;
	      else ++legup;
	    }
	    if( legup == 6*3 ) return true;
	  }

	  return false;
	}

	void Move_Send_Serial() {
		  // send updates to hexapod?
		ByteBuffer buffer = ByteBuffer.allocate(64);
		  int used=0;

		  int i;
		  for(i=0;i<6;++i) {
		    Leg leg=legs[i];

		    // update pan
		    leg.pan_joint.angle=Math.max(Math.min(leg.pan_joint.angle,(float)leg.pan_joint.angle_max),(float)leg.pan_joint.angle_min);
		    if(leg.pan_joint.last_angle!=(int)leg.pan_joint.angle)
		    {
		      leg.pan_joint.last_angle=(int)leg.pan_joint.angle;
		      buffer.put(used++,(byte)leg.pan_joint.servo_address);
		      buffer.put(used++,(byte)leg.pan_joint.angle);
		      //Log3("%d=%d ",buffer[used-2],buffer[used-1]);
		    }

		    // update tilt
		    leg.tilt_joint.angle=Math.max(Math.min(leg.tilt_joint.angle,(float)leg.tilt_joint.angle_max),(float)leg.tilt_joint.angle_min);
		    if(leg.tilt_joint.last_angle!=(int)leg.tilt_joint.angle)
		    {
		      leg.tilt_joint.last_angle=(int)leg.tilt_joint.angle;
		      buffer.put(used++,(byte)leg.tilt_joint.servo_address);
		      buffer.put(used++,(byte)leg.tilt_joint.angle);
		      //Log3("%d=%d ",buffer[used-2],buffer[used-1]);
		    }

		    // update knee
		    leg.knee_joint.angle=Math.max(Math.min(leg.knee_joint.angle,(float)leg.knee_joint.angle_max),(float)leg.knee_joint.angle_min);
		    if(leg.knee_joint.last_angle!=(int)leg.knee_joint.angle)
		    {
		      leg.knee_joint.last_angle=(int)leg.knee_joint.angle;
		      buffer.put(used++,(byte)leg.knee_joint.servo_address);
		      buffer.put(used++,(byte)leg.knee_joint.angle);
		      //Log3("%d=%d ",buffer[used-2],buffer[used-1]);
		    }
		  }

		  if(used>0) {
		    Instruct('U',buffer);
		  }
		}

	void Teleport( Vector3f newpos ) {
		// move a robot to a new position, update all joints.
		newpos.sub( body.pos );
		body.pos.add( newpos );

		int i;
		for( i = 0; i < 6; ++i ) {
			Leg leg = legs[i];
		    leg.pan_joint.pos.add( newpos );
		    leg.tilt_joint.pos.add( newpos );
		    leg.knee_joint.pos.add( newpos );
		    leg.ankle_joint.pos.add( newpos );
		  }
	}
	
	public void move(float dt) {
		if(initialized==false) return;
		/*
		  boolean open=comm.IsOpen();
		  comm.Update(dt);
		  if(!open && comm.IsOpen()) {
		    SendZeros();
		  }*/
/*
		  buttons[BUTTONS_X_POS]     = (int) Input.GetSingleton().GetAxisState("spidee","strafe_left");
		  buttons[BUTTONS_X_NEG]     = (int) Input.GetSingleton().GetAxisState("spidee","strafe_right");
		  buttons[BUTTONS_Y_POS]     = (int) Input.GetSingleton().GetAxisState("spidee","strafe_back");
		  buttons[BUTTONS_Y_NEG]     = (int) Input.GetSingleton().GetAxisState("spidee","strafe_forward");
		  buttons[BUTTONS_Z_POS]     = (int) Input.GetSingleton().GetAxisState("spidee","raise_body");
		  buttons[BUTTONS_Z_NEG]     = (int) Input.GetSingleton().GetAxisState("spidee","lower_body");
		  buttons[BUTTONS_X_ROT_POS] = (int) Input.GetSingleton().GetAxisState("spidee","tilt_up");
		  buttons[BUTTONS_X_ROT_NEG] = (int) Input.GetSingleton().GetAxisState("spidee","tilt_down");
		  buttons[BUTTONS_Z_ROT_POS] = (int) Input.GetSingleton().GetAxisState("spidee","turn_left");
		  buttons[BUTTONS_Z_ROT_NEG] = (int) Input.GetSingleton().GetAxisState("spidee","turn_right");
		  buttons[BUTTONS_Y_ROT_POS] = (int) Input.GetSingleton().GetAxisState("spidee","tilt_left");
		  buttons[BUTTONS_Y_ROT_NEG] = (int) Input.GetSingleton().GetAxisState("spidee","tilt_right");
		  buttons[BUTTONS_0]         = (int) Input.GetSingleton().GetAxisState("spidee","recenter");
*/
		  Byte b=0;
		  switch(move_mode) {
		  case MOVE_MODE_CALIBRATE:  b=0;  break;
		  case MOVE_MODE_SITDOWN  :  b=1;  break;
		  case MOVE_MODE_STANDUP  :  b=2;  break;
		  case MOVE_MODE_BODY     :  b=3;  break;
		  case MOVE_MODE_RIPPLE   :  b=4;  break;
		  case MOVE_MODE_WAVE     :  b=5;  break;
		  case MOVE_MODE_TRIPOD   :  b=6;  break;
		  }
		  ByteBuffer buffer=ByteBuffer.allocate(BUTTONS_MAX+1);
		  buffer.put(0,b);
		  for(int i=0;i<BUTTONS_MAX;++i) {
			  buffer.put(i,(byte)buttons[i]);
		  }
		  buffer.rewind();
		  Instruct('I',buffer);

		/*if(Input.GetSingleton().GetButtonState("spidee","connect") == Input.ButtonState.RELEASED ) {
	      open=comm.IsOpen();
	      if(!open) {
	        comm.Start();
	        comm.SetAutoConnect(true);
	        if(comm.IsOpen()) {
	          SendZeros();
	        }
	      }
	    }*/
	    if(Input.GetSingleton().GetButtonState("spidee","reset_legs")==Input.ButtonState.RELEASED) Reset_Position();
	    if(Input.GetSingleton().GetButtonState("spidee","change_mode_next")==Input.ButtonState.RELEASED) {
	    	switch(move_mode) {
	    	case MOVE_MODE_CALIBRATE:  move_mode = Spidee.MoveModes.MOVE_MODE_SITDOWN  ;  break;
	    	case MOVE_MODE_SITDOWN  :  move_mode = Spidee.MoveModes.MOVE_MODE_STANDUP  ;  break;
	    	case MOVE_MODE_STANDUP  :  move_mode = Spidee.MoveModes.MOVE_MODE_BODY     ;  break;
	    	case MOVE_MODE_BODY     :  move_mode = Spidee.MoveModes.MOVE_MODE_RIPPLE   ;  break;
	    	case MOVE_MODE_RIPPLE   :  move_mode = Spidee.MoveModes.MOVE_MODE_WAVE     ;  break;
	    	case MOVE_MODE_WAVE     :  move_mode = Spidee.MoveModes.MOVE_MODE_TRIPOD   ;  break;
	    	case MOVE_MODE_TRIPOD   :  move_mode = Spidee.MoveModes.MOVE_MODE_CALIBRATE;  break;
	    	}
	      //paused=true;
	    }
	    if(Input.GetSingleton().GetButtonState("spidee","change_mode_prev")==Input.ButtonState.RELEASED) {
	    	switch(move_mode) {
	    	case MOVE_MODE_CALIBRATE:  move_mode = Spidee.MoveModes.MOVE_MODE_TRIPOD   ;  break;
		    case MOVE_MODE_SITDOWN  :  move_mode = Spidee.MoveModes.MOVE_MODE_CALIBRATE;  break;
		    case MOVE_MODE_STANDUP  :  move_mode = Spidee.MoveModes.MOVE_MODE_SITDOWN  ;  break;
		    case MOVE_MODE_BODY     :  move_mode = Spidee.MoveModes.MOVE_MODE_STANDUP  ;  break;
		    case MOVE_MODE_RIPPLE   :  move_mode = Spidee.MoveModes.MOVE_MODE_BODY     ;  break;
		    case MOVE_MODE_WAVE     :  move_mode = Spidee.MoveModes.MOVE_MODE_RIPPLE   ;  break;
		    case MOVE_MODE_TRIPOD   :  move_mode = Spidee.MoveModes.MOVE_MODE_WAVE     ;  break;
	    	}
	      //paused=true;
	    }

	    if(Input.GetSingleton().GetButtonState("spidee","pause")==Input.ButtonState.RELEASED) {
	    	paused = paused ? false : true;
	    }
	    if(Input.GetSingleton().GetButtonState("spidee","one_frame")==Input.ButtonState.RELEASED) paused=false;  // part 1
	    if(Input.GetSingleton().GetButtonState("spidee","speed_up")==Input.ButtonState.RELEASED) speed_scale*=1.25f;
	    if(Input.GetSingleton().GetButtonState("spidee","slow_down")==Input.ButtonState.RELEASED) speed_scale/=1.25f;

	    if( paused == false ) dt*= speed_scale;
	    else dt=0;

	    if(Input.GetSingleton().GetButtonState("spidee","one_frame")==Input.ButtonState.RELEASED) paused=true;  // part 2.  this repeats to set dt non zero for a single frame.
		
		if( dt != 0 ) {
			switch(move_mode) {
			case MOVE_MODE_CALIBRATE:   Move_Calibrate(dt);   break;
			case MOVE_MODE_SITDOWN:     Sit_Down(dt);         break;
			case MOVE_MODE_STANDUP:     Stand_Up(dt);         break;
			case MOVE_MODE_BODY:        Move_Body(dt);        break;
			case MOVE_MODE_RIPPLE:      Ripple_Gait(dt);      break;
			case MOVE_MODE_WAVE:        Wave_Gait(dt);        break;
			case MOVE_MODE_TRIPOD:      Tripod_Gait(dt);      break;
			default: break;
			}
		}
		
		if(move_mode != Spidee.MoveModes.MOVE_MODE_CALIBRATE) {
			Move_Apply_Physics(dt);
			Move_Apply_Constraints(dt);
			Move_Calculate_Angles();
		} else {
			// since we now do all the math on the 'duino, this is only use to move individual joints for calibration.
			Move_Send_Serial();
		}
	}
	
	
	public void render(GL2 gl2) {
		if(initialized==false) {
			Init(gl2);
			return;
		}
		
		gl2.glPushMatrix();

		// parts
		Draw_Head(gl2);
		Draw_Legs(gl2);
		Draw_Body(gl2);

		gl2.glPopMatrix();
	}


	void Draw_Body(GL2 gl2) {
		gl2.glPushMatrix();

		if(!render_meshes) {
			gl2.glDisable(GL2.GL_LIGHTING);
			gl2.glTranslatef(body.pos.x + body.up.x * 1,
	                 body.pos.y + body.up.y * 1,
	                 body.pos.z + body.up.z * 1);
			gl2.glBegin(GL2.GL_LINES);
			gl2.glColor3f(1,0,0);  gl2.glVertex3f(0,0,0);  gl2.glVertex3f(body.forward.x,body.forward.y,body.forward.z);
			gl2.glColor3f(0,1,0);  gl2.glVertex3f(0,0,0);  gl2.glVertex3f(body.up.x,body.up.y,body.up.z);
			gl2.glColor3f(0,0,1);  gl2.glVertex3f(0,0,0);  gl2.glVertex3f(body.left.x,body.left.y,body.left.z);
			gl2.glEnd();

			gl2.glEnable(GL2.GL_LIGHTING);

			gl2.glColor4f(1,1,1,1);

	    // body bottom
	    //gl2.glBegin(GL2.GL_LINE_LOOP);
	    gl2.glNormal3f(body.up.x,body.up.y,body.up.z);
	    gl2.glBegin(GL2.GL_TRIANGLE_FAN);
	    gl2.glVertex3f( - body.up.x*0.02f,
	                - body.up.y*0.02f,
	                - body.up.z*0.02f);
	    int i;
	    for(i=0;i<=32;++i) {
	      float x=i*((float)Math.PI*2.0f)/32.0f;
	      float sx=(float)Math.sin(x)*body_radius;
	      float cx=(float)Math.cos(x)*body_radius;
	      gl2.glVertex3f(sx*body.left.x + cx*body.forward.x - body.up.x*0.02f,
	                 sx*body.left.y + cx*body.forward.y - body.up.y*0.02f,
	                 sx*body.left.z + cx*body.forward.z - body.up.z*0.02f);
	    }
	    gl2.glEnd();

	    // body top
	    //gl2.glBegin(GL2.GL_LINE_LOOP);
	    gl2.glNormal3f(body.up.x,body.up.y,body.up.z);
	    gl2.glBegin(GL2.GL_TRIANGLE_FAN);
	    gl2.glVertex3f( + body.up.x*6.02f,
	                + body.up.y*6.02f,
	                + body.up.z*6.02f);
	    for(i=0;i<=32;++i) {
	      float x=i*((float)Math.PI*2.0f)/32.0f;
	      float sx=(float)Math.sin(x)*body_radius;
	      float cx=(float)Math.cos(x)*body_radius;
	      gl2.glVertex3f(sx*body.left.x + cx*body.forward.x + body.up.x*6.02f,
	                 sx*body.left.y + cx*body.forward.y + body.up.y*6.02f,
	                 sx*body.left.z + cx*body.forward.z + body.up.z*6.02f);
	    }
	    gl2.glEnd();
	  } else {
		FloatBuffer m=FloatBuffer.allocate(16);

		m.put( 0,-body.left.x);
		m.put( 1,-body.left.y);
		m.put( 2,-body.left.z);
		m.put( 4,body.forward.x);
		m.put( 5,body.forward.y);
		m.put( 6,body.forward.z);
		m.put( 8,body.up.x);
		m.put( 9,body.up.y);
		m.put(10,body.up.z);
		m.put(15,1);

	    gl2.glColor3f(1,1,1);
	    gl2.glTranslatef(body.pos.x + 7.5f * body.up.x,
	                 body.pos.y + 7.5f * body.up.y,
	                 body.pos.z + 7.5f * body.up.z );
	    gl2.glMultMatrixf(m);
	    gl2.glRotatef(180,0,1,0);
	    model_body.Draw(gl2);
	  } 
	  gl2.glPopMatrix();
	}


	void Draw_Head(GL2 gl2) {
	  int i;

	  gl2.glPushMatrix();
	  // head
	  Vector3f v=new Vector3f(body.forward);
	  v.scale(10);
	  v.add(body.pos);
	  gl2.glTranslatef(v.x,v.y,v.z);
	  gl2.glColor3f(1.0f,0.8f,0.0f);
	  gl2.glBegin(GL2.GL_LINE_LOOP);
	  for(i=0;i<32;++i) {
	    float x=i*((float)Math.PI*2.0f)/32.0f;
	    gl2.glVertex3f((float)Math.sin(x)*0.5f,(float)Math.cos(x)*0.5f,0.0f);
	  }
	  gl2.glEnd();
	  gl2.glBegin(GL2.GL_LINE_LOOP);
	  for(i=0;i<32;++i) {
	    float x=i*((float)Math.PI*2.0f)/32.0f;
	    gl2.glVertex3f((float)Math.sin(x)*0.5f,0.0f,(float)Math.cos(x)*0.5f);
	  }
	  gl2.glEnd();
	  gl2.glBegin(GL2.GL_LINE_LOOP);
	  for(i=0;i<32;++i) {
	    float x=i*((float)Math.PI*2.0f)/32.0f;
	    gl2.glVertex3f(0.0f,(float)Math.sin(x)*0.5f,(float)Math.cos(x)*0.5f);
	  }
	  gl2.glEnd();
	  gl2.glPopMatrix();
	}


	void Draw_Legs(GL2 gl2) {
	  if(!render_meshes) {/*
	    int i,j;

	    for(i=0;i<6;++i) {
	      Leg leg=legs[i];

	      gl2.glDisable(GL2.GL_LIGHTING);

	      switch(i) {
	      case 0:  gl2.glColor3f(1,0,0);  break;
	      case 1:  gl2.glColor3f(0,1,0);  break;
	      case 2:  gl2.glColor3f(0,0,1);  break;
	      case 3:  gl2.glColor3f(1,1,0);  break;
	      case 4:  gl2.glColor3f(0,1,1);  break;
	      case 5:  gl2.glColor3f(1,0,1);  break;
	      }
	      
	      // last point of contact
	      gl2.glBegin(GL2.GL_LINE_LOOP);
	      gl2.glVertex3f(leg.lpoc.x+0.5f, leg.lpoc.y-0.5f, 0);
	      gl2.glVertex3f(leg.lpoc.x+0.5f, leg.lpoc.y+0.5f, 0);
	      gl2.glVertex3f(leg.lpoc.x-0.5f, leg.lpoc.y+0.5f, 0);
	      gl2.glVertex3f(leg.lpoc.x-0.5f, leg.lpoc.y-0.5f, 0);
	      gl2.glEnd();
	      gl2.glBegin(GL2.GL_LINES);
	      gl2.glVertex3f(leg.npoc.x-1.0f, leg.npoc.y, 0);
	      gl2.glVertex3f(leg.npoc.x+1.0f, leg.npoc.y, 0);
	      gl2.glVertex3f(leg.npoc.x, leg.npoc.y-1.0f, 0);
	      gl2.glVertex3f(leg.npoc.x, leg.npoc.y+1.0f, 0);
	      gl2.glEnd();

	      // next point of contact
	      gl2.glBegin(GL2.GL_LINE_LOOP);
	      gl2.glVertex3f(leg.npoc.x+0.75f, leg.npoc.y-0.75f, 0);
	      gl2.glVertex3f(leg.npoc.x+0.75f, leg.npoc.y+0.75f, 0);
	      gl2.glVertex3f(leg.npoc.x-0.75f, leg.npoc.y+0.75f, 0);
	      gl2.glVertex3f(leg.npoc.x-0.75f, leg.npoc.y-0.75f, 0);
	      gl2.glEnd();

	      gl2.glBegin(GL2.GL_LINES);
	      Vector3f under = new Vector3f(leg.ankle_joint.pos);
	      under.z=0;
	      gl2.glVertex3f(leg.ankle_joint.pos.x,leg.ankle_joint.pos.y,leg.ankle_joint.pos.z);
	      gl2.glVertex3f(under.x,under.y,under.z);
	      gl2.glEnd();

	      leg.pan_joint.Draw(gl2,2);
	      leg.tilt_joint.Draw(gl2,1);
	      leg.knee_joint.Draw(gl2,3);

	      gl2.glEnable(GL2.GL_LIGHTING);
	      
	      // shoulder
	      //gl2.glBegin(GL2.GL_LINE_LOOP);
	      gl2.glBegin(GL2.GL_QUADS);
	      gl2.glColor3f(0,1,0);
	      gl2.glNormal3f( -leg.pan_joint.up.x,-leg.pan_joint.up.y,-leg.pan_joint.up.z );
	      gl2.glVertex3f( leg.pan_joint.pos + leg.pan_joint.forward * 3.232f + leg.pan_joint.left * 1.5f + leg.pan_joint.up * -1.0 );
	      gl2.glVertex3f( leg.pan_joint.pos + leg.pan_joint.forward * 3.232f - leg.pan_joint.left * 1.5f + leg.pan_joint.up * -1.0 );
	      gl2.glVertex3f( leg.pan_joint.pos - leg.pan_joint.forward * 1.000f - leg.pan_joint.left * 1.5f + leg.pan_joint.up * -1.0 );
	      gl2.glVertex3f( leg.pan_joint.pos - leg.pan_joint.forward * 1.000f + leg.pan_joint.left * 1.5f + leg.pan_joint.up * -1.0 );

	      gl2.glNormal3f( leg.pan_joint.up.x,leg.pan_joint.up.y,leg.pan_joint.up.z );
	      gl2.glVertex3f( leg.pan_joint.pos + leg.pan_joint.forward * 3.232f + leg.pan_joint.left * 1.5f + leg.pan_joint.up * 5.0 );
	      gl2.glVertex3f( leg.pan_joint.pos + leg.pan_joint.forward * 3.232f - leg.pan_joint.left * 1.5f + leg.pan_joint.up * 5.0 );
	      gl2.glVertex3f( leg.pan_joint.pos - leg.pan_joint.forward * 1.000f - leg.pan_joint.left * 1.5f + leg.pan_joint.up * 5.0 );
	      gl2.glVertex3f( leg.pan_joint.pos - leg.pan_joint.forward * 1.000f + leg.pan_joint.left * 1.5f + leg.pan_joint.up * 5.0 );
	      gl2.glEnd();

	      gl2.glBegin(GL2.GL_QUADS);
	      gl2.glColor3f(0,1,0);
	      gl2.glNormal3f( leg.pan_joint.forward.x,leg.pan_joint.forward.y,leg.pan_joint.forward.z );
	      gl2.glVertex3f( leg.pan_joint.pos + leg.pan_joint.forward * 3.232f + leg.pan_joint.left * 1.5f + leg.pan_joint.up * -1.0 );
	      gl2.glVertex3f( leg.pan_joint.pos + leg.pan_joint.forward * 3.232f - leg.pan_joint.left * 1.5f + leg.pan_joint.up * -1.0 );
	      gl2.glVertex3f( leg.pan_joint.pos + leg.pan_joint.forward * 3.232f - leg.pan_joint.left * 1.5f + leg.pan_joint.up * 5.0 );
	      gl2.glVertex3f( leg.pan_joint.pos + leg.pan_joint.forward * 3.232f + leg.pan_joint.left * 1.5f + leg.pan_joint.up * 5.0 );

	      gl2.glNormal3f( -leg.pan_joint.forward.x,-leg.pan_joint.forward.y,-leg.pan_joint.forward.z );
	      gl2.glVertex3f( leg.pan_joint.pos - leg.pan_joint.forward * 1.000f - leg.pan_joint.left * 1.5f + leg.pan_joint.up * -1.0 );
	      gl2.glVertex3f( leg.pan_joint.pos - leg.pan_joint.forward * 1.000f + leg.pan_joint.left * 1.5f + leg.pan_joint.up * -1.0 );
	      gl2.glVertex3f( leg.pan_joint.pos - leg.pan_joint.forward * 1.000f + leg.pan_joint.left * 1.5f + leg.pan_joint.up * 5.0 );
	      gl2.glVertex3f( leg.pan_joint.pos - leg.pan_joint.forward * 1.000f - leg.pan_joint.left * 1.5f + leg.pan_joint.up * 5.0 );
	      gl2.glEnd();

	      gl2.glBegin(GL2.GL_QUADS);
	      gl2.glColor3f(0,1,0);
	      gl2.glNormal3f( leg.pan_joint.left.x,leg.pan_joint.left.y,leg.pan_joint.left.z );
	      gl2.glVertex3f( leg.pan_joint.pos + leg.pan_joint.forward * 3.232f + leg.pan_joint.left * 1.5f + leg.pan_joint.up * -1.0 );
	      gl2.glVertex3f( leg.pan_joint.pos + leg.pan_joint.forward * 3.232f + leg.pan_joint.left * 1.5f + leg.pan_joint.up * 5.0 );
	      gl2.glVertex3f( leg.pan_joint.pos - leg.pan_joint.forward * 1.000f + leg.pan_joint.left * 1.5f + leg.pan_joint.up * 5.0 );
	      gl2.glVertex3f( leg.pan_joint.pos - leg.pan_joint.forward * 1.000f + leg.pan_joint.left * 1.5f + leg.pan_joint.up * -1.0 );

	      gl2.glNormal3f( -leg.pan_joint.left.x,-leg.pan_joint.left.y,-leg.pan_joint.left.z );
	      gl2.glVertex3f( leg.pan_joint.pos + leg.pan_joint.forward * 3.232f - leg.pan_joint.left * 1.5f + leg.pan_joint.up * -1.0 );
	      gl2.glVertex3f( leg.pan_joint.pos + leg.pan_joint.forward * 3.232f - leg.pan_joint.left * 1.5f + leg.pan_joint.up * 5.0 );
	      gl2.glVertex3f( leg.pan_joint.pos - leg.pan_joint.forward * 1.000f - leg.pan_joint.left * 1.5f + leg.pan_joint.up * 5.0 );
	      gl2.glVertex3f( leg.pan_joint.pos - leg.pan_joint.forward * 1.000f - leg.pan_joint.left * 1.5f + leg.pan_joint.up * -1.0 );
	      gl2.glEnd();
	      

	      // femur
	      gl2.glNormal3f( leg.tilt_joint.left.x,leg.tilt_joint.left.y,leg.tilt_joint.left.z);
	      Vector3f pivot = new Vector3f(leg.tilt_joint.up);
	      pivot.scale(5);
	      pivot.add(leg.tilt_joint.pos);
	      gl2.glBegin(GL2.GL_TRIANGLE_STRIP);
	      gl2.glColor4f(1,0,0,1);
	      for(j=0;j<=16;++j) {
	    	  float js=(float)Math.sin(j*(float)Math.PI/32.0f);
	    	  float jc=(float)Math.cos(j*(float)Math.PI/32.0f);
	    	  gl2.glVertex3f( pivot - leg.tilt_joint.up * (4.0f * js) + leg.tilt_joint.forward * (4.0f * jc) + leg.tilt_joint.left * 2.0f );
	    	  gl2.glVertex3f( pivot - leg.tilt_joint.up * (6.0f * js) + leg.tilt_joint.forward * (6.0f * jc) + leg.tilt_joint.left * 2.0f );
	      }
	      gl2.glEnd();
	      gl2.glBegin(GL2.GL_TRIANGLE_STRIP);
	      for(j=0;j<=16;++j) {
	    	  float js=(float)Math.sin(j*(float)Math.PI/32.0f);
	    	  float jc=(float)Math.cos(j*(float)Math.PI/32.0f);
	    	  gl2.glVertex3f( pivot - leg.tilt_joint.up * (4.0f * js) + leg.tilt_joint.forward * (4.0f * jc) + leg.tilt_joint.left * -2.0f );
	    	  gl2.glVertex3f( pivot - leg.tilt_joint.up * (6.0f * js) + leg.tilt_joint.forward * (6.0f * jc) + leg.tilt_joint.left * -2.0f );
	      }
	      gl2.glEnd();

	      if(leg.active) {
	        // femur outline
	        gl2.glDisable(GL2.GL_LIGHTING);
	        Vector3f pivot( leg.tilt_joint.pos + leg.tilt_joint.up * 5.5f );
	        gl2.glBegin(GL2.GL_LINE_LOOP);
	        gl2.glColor3f(1,1,0);
	        for(j=0;j<=16;++j) {
	          float js=sin(j*PI/32);
	          float jc=cos(j*PI/32);
	          gl2.glVertex3fv( pivot - leg.tilt_joint.up * (4.0f * js) + leg.tilt_joint.forward * (4.0f * jc) + leg.tilt_joint.left * 2.0f );
	        }
	        for(j=16;j>=0;--j) {
	          float js=sin(j*PI/32);
	          float jc=cos(j*PI/32);
	          gl2.glVertex3fv( pivot - leg.tilt_joint.up * (6.0f * js) + leg.tilt_joint.forward * (6.0f * jc) + leg.tilt_joint.left * 2.0f );
	        }
	        gl2.glEnd();
	        gl2.glBegin(GL2.GL_LINE_LOOP);
	        gl2.glColor3f(1,1,0);
	        for(j=0;j<=16;++j) {
	          float js=sin(j*PI/32);
	          float jc=cos(j*PI/32);
	          gl2.glVertex3fv( pivot - leg.tilt_joint.up * (4.0f * js) + leg.tilt_joint.forward * (4.0f * jc) + leg.tilt_joint.left * -2.0f );
	        }
	        for(j=16;j>=0;--j) {
	          float js=sin(j*PI/32);
	          float jc=cos(j*PI/32);
	          gl2.glVertex3fv( pivot - leg.tilt_joint.up * (6.0f * js) + leg.tilt_joint.forward * (6.0f * jc) + leg.tilt_joint.left * -2.0f );
	        }
	        gl2.glEnd();
	        gl2.glEnable(GL2.GL_LIGHTING);
	      }

	      // tibia
	      gl2.glNormal3fv(leg.knee_joint.left );
	      gl2.glBegin(GL2.GL_POLYGON);
	      gl2.glColor4f(0,0,1,1);
	      gl2.glVertex3fv( leg.knee_joint.pos + leg.knee_joint.up *  1.1f + leg.knee_joint.forward * -3.0f );
	      gl2.glVertex3fv( leg.knee_joint.pos + leg.knee_joint.up *  1.1f + leg.knee_joint.forward *  0.0f );
	      gl2.glVertex3fv( leg.knee_joint.pos                             + leg.knee_joint.forward * 10.0f );
	      gl2.glVertex3fv( leg.knee_joint.pos + leg.knee_joint.up * -1.1f + leg.knee_joint.forward *  0.0f );
	      gl2.glVertex3fv( leg.knee_joint.pos + leg.knee_joint.up * -1.1f + leg.knee_joint.forward * -3.0f );
	      gl2.glEnd();

	      if(leg.active) {
	        gl2.glDisable(GL2.GL_LIGHTING);
	        // tibia outline
	        gl2.glBegin(GL2.GL_LINE_LOOP);
	        gl2.glColor3f(1,1,0);
	        gl2.glVertex3fv( leg.knee_joint.pos + leg.knee_joint.up *  1.1f + leg.knee_joint.forward * -3.0f );
	        gl2.glVertex3fv( leg.knee_joint.pos + leg.knee_joint.up *  1.1f + leg.knee_joint.forward *  0.0f );
	        gl2.glVertex3fv( leg.knee_joint.pos                             + leg.knee_joint.forward * 10.0f );
	        gl2.glVertex3fv( leg.knee_joint.pos + leg.knee_joint.up * -1.1f + leg.knee_joint.forward *  0.0f );
	        gl2.glVertex3fv( leg.knee_joint.pos + leg.knee_joint.up * -1.1f + leg.knee_joint.forward * -3.0f );
	        gl2.glEnd();
	        gl2.glEnable(GL2.GL_LIGHTING);
	      }
	    }*/
	  } else {
	    int i;
	    FloatBuffer m = FloatBuffer.allocate(16);

	    for(i=0;i<6;++i) {
	      Leg leg = legs[i];
	      leg.Draw(gl2,i);

	      gl2.glPushMatrix();
	      gl2.glColor3f(0,1,0);
	      gl2.glTranslatef(leg.pan_joint.pos.x,
	                   		leg.pan_joint.pos.y,
	                   		leg.pan_joint.pos.z);
	      gl2.glTranslatef(leg.pan_joint.up.x*2.5f,
	                   		leg.pan_joint.up.y*2.5f,
	                   		leg.pan_joint.up.z*2.5f);

	      if(i<3) {
	        gl2.glTranslatef(leg.pan_joint.forward.x*-1.0f,
	                     leg.pan_joint.forward.y*-1.0f,
	                     leg.pan_joint.forward.z*-1.0f);
	        gl2.glTranslatef(leg.pan_joint.left.x*-1.0f,
	                     leg.pan_joint.left.y*-1.0f,
	                     leg.pan_joint.left.z*-1.0f);
	        m.put( 0,-leg.pan_joint.left.x);
	        m.put( 1,-leg.pan_joint.left.y);
	        m.put( 2,-leg.pan_joint.left.z); 
	        m.put( 4,leg.pan_joint.up.x);
	        m.put( 5,leg.pan_joint.up.y);
	        m.put( 6,leg.pan_joint.up.z);
	        m.put( 8,leg.pan_joint.forward.x); 
	        m.put( 9,leg.pan_joint.forward.y);
	        m.put(10,leg.pan_joint.forward.z);
	      } else {
	        gl2.glTranslatef(leg.pan_joint.forward.x*1.3f,
	                     leg.pan_joint.forward.y*1.3f,
	                     leg.pan_joint.forward.z*1.3f);
	        gl2.glTranslatef(leg.pan_joint.left.x*1.1f,
	                     leg.pan_joint.left.y*1.1f,
	                     leg.pan_joint.left.z*1.1f);
	        
	        m.put( 0,leg.pan_joint.left.x);
	        m.put( 1,leg.pan_joint.left.y);
	        m.put( 2,leg.pan_joint.left.z); 
	        m.put( 4,leg.pan_joint.up.x);
	        m.put( 5,leg.pan_joint.up.y);
	        m.put( 6,leg.pan_joint.up.z);
	        m.put( 8,-leg.pan_joint.forward.x); 
	        m.put( 9,-leg.pan_joint.forward.y);
	        m.put(10,-leg.pan_joint.forward.z);
	      }
	      m.put( 3,0);
	      m.put( 7,0);
	      m.put(11,0);
	      m.put(12,0);
	      m.put(13,0);
	      m.put(14,0);
	      m.put(15,1);
	      gl2.glMultMatrixf(m);

	      if(i<3) model_shoulder_left.Draw(gl2);
	      else    model_shoulder_right.Draw(gl2);
	      gl2.glPopMatrix();

	      // thigh
	      gl2.glPushMatrix();
	      gl2.glColor3f(1,0,0);
	      gl2.glTranslatef(leg.tilt_joint.pos.x,
		                   leg.tilt_joint.pos.y,
		                   leg.tilt_joint.pos.z);
	      gl2.glTranslatef(leg.pan_joint.left.x*2.0f,
		                   leg.pan_joint.left.y*2.0f,
		                   leg.pan_joint.left.z*2.0f);
	      gl2.glTranslatef(leg.pan_joint.forward.x*1.0f,
		                   leg.pan_joint.forward.y*1.0f,
		                   leg.pan_joint.forward.z*1.0f);
	      gl2.glTranslatef(leg.pan_joint.up.x*0.5f,
		                   leg.pan_joint.up.y*0.5f,
		                   leg.pan_joint.up.z*0.5f);

	      m.put( 0,leg.tilt_joint.up.x);
	      m.put( 1,leg.tilt_joint.up.y);
	      m.put( 2,leg.tilt_joint.up.z);
	      m.put( 4,leg.tilt_joint.left.x);
	      m.put( 5,leg.tilt_joint.left.y);
	      m.put( 6,leg.tilt_joint.left.z); 
	      m.put( 8,-leg.tilt_joint.forward.x); 
	      m.put( 9,-leg.tilt_joint.forward.y);
	      m.put(10,-leg.tilt_joint.forward.z);
	      m.put( 3,0);
	      m.put( 7,0);
	      m.put(11,0);
	      m.put(12,0);
	      m.put(13,0);
	      m.put(14,0);
	      m.put(15,1);
	        
	      gl2.glMultMatrixf(m);

	      model_thigh.Draw(gl2);
	      gl2.glPopMatrix();
	      
	      gl2.glPushMatrix();
	      gl2.glColor3f(1,0,0);
	      gl2.glTranslatef(leg.tilt_joint.pos.x,
		                   leg.tilt_joint.pos.y,
		                   leg.tilt_joint.pos.z);
	      gl2.glTranslatef(leg.tilt_joint.left.x*-2.0f,
		                   leg.tilt_joint.left.y*-2.0f,
		                   leg.tilt_joint.left.z*-2.0f);
	      gl2.glTranslatef(leg.tilt_joint.forward.x*0.8f,
		                   leg.tilt_joint.forward.y*0.8f,
		                   leg.tilt_joint.forward.z*0.8f);
	      gl2.glTranslatef(leg.tilt_joint.up.x*0.5f,
		                   leg.tilt_joint.up.y*0.5f,
		                   leg.tilt_joint.up.z*0.5f);

	      m.put( 0,leg.tilt_joint.up.x);
	      m.put( 1,leg.tilt_joint.up.y);
	      m.put( 2,leg.tilt_joint.up.z);
	      m.put( 4,-leg.tilt_joint.left.x);
	      m.put( 5,-leg.tilt_joint.left.y);
	      m.put( 6,-leg.tilt_joint.left.z); 
	      m.put( 8,-leg.tilt_joint.forward.x); 
	      m.put( 9,-leg.tilt_joint.forward.y);
	      m.put(10,-leg.tilt_joint.forward.z);
	      m.put( 3,0);
	      m.put( 7,0);
	      m.put(11,0);
	      m.put(12,0);
	      m.put(13,0);
	      m.put(14,0);
	      m.put(15,1);
		    
	      gl2.glMultMatrixf(m);

	      model_thigh.Draw(gl2);
	      gl2.glPopMatrix();

	      // shin
	      gl2.glPushMatrix();
	      gl2.glColor3f(0,0,1);
	      gl2.glTranslatef(leg.knee_joint.pos.x,
	                   leg.knee_joint.pos.y,
	                   leg.knee_joint.pos.z);

	      if(i<3) {
	        gl2.glTranslatef(leg.knee_joint.forward.x*-0.75f,
	                     	 leg.knee_joint.forward.y*-0.75f,
	                     	 leg.knee_joint.forward.z*-0.75f);
	        m.put( 0,-leg.knee_joint.forward.x);
	        m.put( 1,-leg.knee_joint.forward.y);
	        m.put( 2,-leg.knee_joint.forward.z);  
	        m.put( 4,leg.knee_joint.left.x);
	        m.put( 5,leg.knee_joint.left.y);
	        m.put( 6,leg.knee_joint.left.z);
	        m.put( 8,leg.knee_joint.up.x); 
	        m.put( 9,leg.knee_joint.up.y);
	        m.put(10,leg.knee_joint.up.z);
	      } else {
	        gl2.glTranslatef(leg.knee_joint.up.x*2.0f,
	                     	 leg.knee_joint.up.y*2.0f,
	                     	 leg.knee_joint.up.z*2.0f);
	        gl2.glTranslatef(leg.knee_joint.forward.x*-0.75f,
	                     	 leg.knee_joint.forward.y*-0.75f,
	                     	 leg.knee_joint.forward.z*-0.75f);
	        m.put( 0,-leg.knee_joint.forward.x);
	        m.put( 1,-leg.knee_joint.forward.y);
	        m.put( 2,-leg.knee_joint.forward.z);  
	        m.put( 4,-leg.knee_joint.left.x);
	        m.put( 5,-leg.knee_joint.left.y);
	        m.put( 6,-leg.knee_joint.left.z);
	        m.put( 8,-leg.knee_joint.up.x); 
	        m.put( 9,-leg.knee_joint.up.y);
	        m.put(10,-leg.knee_joint.up.z);
	      }
	      m.put( 3,0);
	      m.put( 7,0);
	      m.put(11,0);
	      m.put(12,0);
	      m.put(13,0);
	      m.put(14,0);
	      m.put(15,1);
		    
	      gl2.glMultMatrixf(m);
	      if(i<3) model_shin_left.Draw(gl2);
	      else    model_shin_right.Draw(gl2);
	      gl2.glPopMatrix();
	    }
	  }
	}
	


	// send the calibration data to the robot
	void SendZeros() {
	  System.out.print("Sending Zeros...\n");
	  ByteBuffer zeros = ByteBuffer.allocate(18*2);
	  int j=0;
	  for(int i=0;i<6;++i) {
	    zeros.put(j++,(byte)legs[i].pan_joint.servo_address);
	    zeros.put(j++,(byte)legs[i].pan_joint.zero);
	    zeros.put(j++,(byte)legs[i].tilt_joint.servo_address);
	    zeros.put(j++,(byte)legs[i].tilt_joint.zero);
	    zeros.put(j++,(byte)legs[i].knee_joint.servo_address);
	    zeros.put(j++,(byte)legs[i].knee_joint.zero);
	    System.out.println(""+legs[i].pan_joint.servo_address+":"+legs[i].pan_joint.zero);
	    System.out.println(""+legs[i].tilt_joint.servo_address+":"+legs[i].tilt_joint.zero);
	    System.out.println(""+legs[i].knee_joint.servo_address+":"+legs[i].knee_joint.zero);
	  }

	  zeros.rewind();
	  Instruct('W',zeros);
	}



	void Reset_Position() {
		ByteBuffer buffer=ByteBuffer.allocate(6*6);

	  gait_cycle=0;

	  // Reset the bot position
	  int i,j=0;
	  for(i=0;i<6;++i) {
	    buffer.put(j++,(byte)legs[i].pan_joint .servo_address);  buffer.put(j++,(byte)(legs[i].pan_joint .zero    ));
	    buffer.put(j++,(byte)legs[i].tilt_joint.servo_address);  buffer.put(j++,(byte)(legs[i].tilt_joint.zero+ 33));
	    buffer.put(j++,(byte)legs[i].knee_joint.servo_address);  buffer.put(j++,(byte)(legs[i].knee_joint.zero+120));
	  }

	  buffer.rewind();
	  Instruct('U',buffer);
	}

	
	void Instruct(char code,ByteBuffer buf) {
		//throw new NotImplementedException();
	}


	void Record_Calibration() {
	  for(int i=0;i<6;++i) {
	    //legs[i].pan_joint .zero;
	    legs[i].tilt_joint.zero-=33;
	    legs[i].knee_joint.zero-=120;
	  }
	}


	void Move_Calibrate(float dt) {
		// turn active legs on and off.
		float a=0, b=0, c=0;

		if(Input.GetSingleton().GetButtonState("spidee","leg1")==Input.ButtonState.RELEASED) legs[0].active = legs[0].active ? false : true;
	    if(Input.GetSingleton().GetButtonState("spidee","leg2")==Input.ButtonState.RELEASED) legs[1].active = legs[1].active ? false : true;
	    if(Input.GetSingleton().GetButtonState("spidee","leg3")==Input.ButtonState.RELEASED) legs[2].active = legs[2].active ? false : true;
	    if(Input.GetSingleton().GetButtonState("spidee","leg4")==Input.ButtonState.RELEASED) legs[3].active = legs[3].active ? false : true;
	    if(Input.GetSingleton().GetButtonState("spidee","leg5")==Input.ButtonState.RELEASED) legs[4].active = legs[4].active ? false : true;
	    if(Input.GetSingleton().GetButtonState("spidee","leg6")==Input.ButtonState.RELEASED) legs[5].active = legs[5].active ? false : true;

	    a=(float)Input.GetSingleton().GetAxisState("spidee","strafe_back"   )
	     -(float)Input.GetSingleton().GetAxisState("spidee","strafe_forward");
	    b=(float)Input.GetSingleton().GetAxisState("spidee","lower_body"    )
	     -(float)Input.GetSingleton().GetAxisState("spidee","raise_body"    );
	    c=(float)Input.GetSingleton().GetAxisState("spidee","strafe_right"  )
	     -(float)Input.GetSingleton().GetAxisState("spidee","strafe_left"   );

	    if(Input.GetSingleton().GetButtonState("spidee","calibrate")==Input.ButtonState.RELEASED) {
	    	for(int i=0;i<6;++i) {
	    		Preferences pn = prefs.node("Leg "+i);
	    		legs[i].pan_joint.zero=legs[i].pan_joint.angle;
	    		legs[i].tilt_joint.zero=legs[i].tilt_joint.angle;
	    		legs[i].knee_joint.zero=legs[i].knee_joint.angle;
	    		pn.putFloat( "pan_zero",legs[i].pan_joint .zero);
	    		pn.putFloat("tilt_zero",legs[i].tilt_joint.zero);
	    		pn.putFloat("knee_zero",legs[i].knee_joint.zero);
	    	}

	    	System.out.println("Updating calibration...");
	    	SendZeros();
	    }

		if(a!=0 || b!=0 || c!=0) {
		    int i;
		    for(i=0;i<6;++i) {
		    	Leg leg=legs[i];
		    	if(leg.active) {
		    		// limit the max vel
			        /*
			        leg.ankle_joint.pos += leg.pan_joint.forward * ( a * dt );
			        leg.ankle_joint.pos += leg.tilt_joint.left * ( c * dt );
			        leg.ankle_joint.pos.z += ( b * dt );
			        */
			        leg.pan_joint.angle  += c;
			        leg.tilt_joint.angle += a;
			        leg.knee_joint.angle += b;
		    	}
		    }
		}
	}


	void Update_Gait_Target(float dt,float move_body_scale) {
	  if( Input.GetSingleton().GetButtonState("camera","active")==Input.ButtonState.ON) return;

	  int turn_direction = buttons[BUTTONS_Z_ROT_POS]
	                     - buttons[BUTTONS_Z_ROT_NEG];
	  int walk_direction = buttons[BUTTONS_Y_NEG]
	                     - buttons[BUTTONS_Y_POS];
	  int strafe_direction = buttons[BUTTONS_X_POS]
	                       - buttons[BUTTONS_X_NEG];

	  boolean update=( turn_direction != 0 || walk_direction != 0 || strafe_direction != 0 );

	  // zero stance width
	  if(buttons[BUTTONS_1]>0) {  // widen stance
	    standing_radius+=dt*4;
	    update=true;
	  }
	  if(buttons[BUTTONS_2]>0) {  // narrow stance
	    standing_radius-=dt*4;
	    if( standing_radius < body_radius + 1 ) {
	      standing_radius = body_radius + 1;
	    }
	    update=true;
	  }

	  // zero body standing height
	  if(buttons[BUTTONS_Z_POS]>0) {  // raise body
	    standing_height+=dt*4;
	    update=true;
	  }
	  if(buttons[BUTTONS_Z_NEG]>0) {  // lower body
	    standing_height-=dt*4;
	    if( standing_height < 1.5f ) {
	      standing_height = 1.5f;
	    }
	    update=true;
	  }

	  if(!update) return;

	  int i;

	  for(i=0;i<6;++i) {
	    Leg leg = legs[i];
	    Vector3f ds = new Vector3f( leg.pan_joint.pos );
	    ds.sub( body.pos );
	    ds.normalize();
	    ds.scale( standing_radius );
	    leg.npoc.set( body.pos );
	    leg.npoc.add(ds);
	    leg.npoc.z=0;
	  }

	  // turn
	  if( turn_direction != 0 ) {
	    turn_direction = (int)Math.max(Math.min( (float)turn_direction, 180*dt), -180*dt );
	    float turn = turn_direction * turn_stride_length * DEG2RAD * dt * move_body_scale / 6.0f;

	    float c=( (float)Math.cos( turn ) );
	    float s=( (float)Math.sin( turn ) );

	    for(i=0;i<6;++i) {
	      Leg leg = legs[i];
	      Vector3f df= new Vector3f( leg.npoc );
	      df.sub( body.pos );
	      df.z = 0;
	      leg.npoc.x = df.x *  c + df.y * -s;
	      leg.npoc.y = df.x *  s + df.y * c;
	      leg.npoc.add(body.pos);
	    }

	    Vector3f df= new Vector3f( body.forward );
	    df.z = 0;
	    target.forward.x = df.x *  c + df.y * -s;
	    target.forward.y = df.x *  s + df.y *  c;
	    target.forward.normalize();
	    target.left.cross(body.up, target.forward);
	    }

	  // translate
	  Vector3f dir= new Vector3f(0,0,0);

	  if(   walk_direction > 0 ) dir.add(body.forward);  // forward
	  if(   walk_direction < 0 ) dir.sub(body.forward);  // backward
	  if( strafe_direction > 0 ) dir.add(body.left);  // strafe left
	  if( strafe_direction < 0 ) dir.sub(body.left);  // strafe right

	  dir.z=0;
	  if(dir.length() > 0.001f ) {
		  dir.normalize();
	  }

	  Vector3f p = new Vector3f(0,0,0);
//	  float zi=0;
	  
	  for(i=0;i<6;++i) {
	    Leg leg = legs[i];
	    leg.npoc.x+= dir.x * ( stride_length*dt );
	    leg.npoc.y+= dir.y * ( stride_length*dt );
	    leg.npoc.z=0;

	    Vector3f ptemp= new Vector3f(leg.ankle_joint.pos);
	    if(leg.on_ground) {
//	    	++zi;
		} else ptemp.z=0;
	    p.add(ptemp);
	  }

	  Vector3f t= new Vector3f(dir);
	  t.scale( stride_length*dt * move_body_scale / 6.0f );
	  //*
	  body.pos.add(t);
	  body.pos.z = standing_height;
	  /*/
	  float z=p.z;
	  p/=6;
	  if(zi>0) p.z=z/zi;
	  body.pos=p + body.up * standing_height;
	  //*/
	}



	void Update_Gait_Target_Goto(Vector3f destination,float dt,float move_body_scale) {
		Vector3f dp = new Vector3f(destination);
		dp.sub(body.pos);
	  float turn_direction = dp.dot( body.left );
	  float walk_direction = dp.dot( body.forward );
	  float strafe_direction = dp.dot( body.left );

	  int i;

	  for(i=0;i<6;++i) {
	    Leg leg = legs[i];
	    Vector3f ds = new Vector3f( leg.pan_joint.pos );
	    ds.sub( body.pos );
	    ds.normalize();
	    ds.scale(standing_radius);
	    leg.npoc.set(body.pos);
	    leg.npoc.add( ds );
	    leg.npoc.z=0;
	  }

	  // turn
	  if( turn_direction != 0 ) {
	    turn_direction= (float)Math.max( Math.min( turn_direction, 180*dt ), -180*dt );
	    float turn = turn_direction * turn_stride_length * DEG2RAD * dt * move_body_scale / 6.0f;

	    float c= (float)Math.cos( turn );
	    float s= (float)Math.sin( turn );

	    for(i=0;i<6;++i) {
	      Leg leg = legs[i];
	      Vector3f df = new Vector3f( leg.npoc );
	      df.sub( body.pos );
	      df.z = 0;
	      leg.npoc.x = df.x *  c + df.y * -s;
	      leg.npoc.y = df.x *  s + df.y * c;
	      leg.npoc.add( body.pos );
	    }

	    Vector3f df = new Vector3f( body.forward );
	    df.z = 0;
	    target.forward.x = df.x *  c + df.y * -s;
	    target.forward.y = df.x *  s + df.y *  c;
	    target.forward.normalize();
	    target.left.cross(body.up, target.forward);
	  }

	  // translate
	  Vector3f dir = new Vector3f(0,0,0);

	  if( walk_direction > 0 ) dir.add(body.forward);  // forward
	  if( walk_direction < 0 ) dir.sub(body.forward);  // backward
	  if( strafe_direction > 0 ) dir.add(body.left);  // strafe left
	  if( strafe_direction < 0 ) dir.sub(body.left);  // strafe right

	  dir.z=0;
	  dir.normalize();
	  Vector3f p = new Vector3f(0,0,0);
	  float zi=0;

	  for(i=0;i<6;++i) {
	    Leg leg = legs[i];
	    Vector3f t = new Vector3f(dir);
	    t.scale(stride_length*dt);
	    leg.npoc.add(t);
	    leg.npoc.z=0;

	    Vector3f ptemp = new Vector3f(leg.ankle_joint.pos);
	    if(leg.on_ground) ++zi;
	    else ptemp.z=0;
	    p.add(ptemp);
	  }

	  //body.pos += dir * ( stride_length*dt * move_body_scale / 6.0f );
	  float z=p.z;
	  p.scale(1.0f/6.0f);
	  if(zi>0) p.z=z/zi;

	  Vector3f t = new Vector3f(body.up);
	  t.scale(standing_height);
	  body.pos.set(p);
	  body.pos.add(t);
	}
	

	void Plant_Feet() {
		int i;
		for(i=0;i<6;++i) {
			legs[i].ankle_joint.pos.z = 0;
		}
	}


	void Update_Gait_Leg(int leg_index,float step,float dt) {
	  Leg leg=legs[leg_index];
	  float step_adj = ( step <= 0.5f ) ? step : 1 - step;
	  step_adj = (float)Math.sin( step_adj * Math.PI );

	  // if we do nothing else, robot will march in place.

	  Vector3f dp = new Vector3f( leg.npoc );
	  dp.sub( leg.ankle_joint.pos );
	  dp.z=0;
	  dp.scale( step );

	  leg.ankle_joint.pos.add( dp );
	  leg.ankle_joint.pos.z = step_adj * stride_height;
	}



	void Ripple_Gait(float dt) {
	  gait_cycle+=dt;

	  Update_Gait_Target(dt,1.0f/6.0f);

	  float step=( gait_cycle - (float)Math.floor( gait_cycle ) );
	  int leg_to_move=( (int)Math.floor( gait_cycle ) % 6 );

	  // put all feet down except the "active" leg(s).
	  int i;
	  for(i=0;i<6;++i) {
	    if( i != leg_to_move ) {
	      legs[ i ].ankle_joint.pos.z=0;
	      continue;
	    }
	    Update_Gait_Leg(i,step,dt);
	  }

	  //Center_Body_Around_Feet(dt);
	  if( paused ) {
	    Plant_Feet();
	  }
	}


	void Wave_Gait(float dt) {
	  gait_cycle+=dt;

	  Update_Gait_Target(dt,2.0f/6.0f);

	  float gc1=gait_cycle+0.5f;
	  float gc2=gait_cycle;

	  float x1 = gc1 - (float)Math.floor( gc1 );
	  float x2 = gc2 - (float)Math.floor( gc2 );
	  float step1 = (float)Math.max( 0, x1 );
	  float step2 = (float)Math.max( 0, x2 );
	  int leg1 = (int)Math.floor( gc1 ) % 3;
	  int leg2 = (int)Math.floor( gc2 ) % 3;

	  // Put all feet down except the "active" leg(s).
	  int i;
	  for(i=0;i<6;++i) {
	    if( i != leg1 && i != leg2 ) {
	      legs[ i ].ankle_joint.pos.z=0;
	    }
	  }

	  // 0   5
	  // 1 x 4
	  // 2   3
	  // order should be 0,3,1,5,2,4	  
	  int o1,o2;
	  switch(leg1) {
	  case 0: o1 = 0; break;
	  case 1: o1 = 1; break;
	  case 2: o1 = 2; break;
	  default: o1=0; break;
	  }
	  switch(leg2) {
	  case 0: o2 = 3; break;
	  case 1: o2 = 5; break;
	  case 2: o2 = 4; break;
	  default: o2=0; break;
	  }
	  
	  Update_Gait_Leg( o1, step1, dt );
	  Update_Gait_Leg( o2, step2, dt );

	  //Center_Body_Around_Feet(dt);
	  if( paused ) {
	    Plant_Feet();
	  }
	}



	void Tripod_Gait(float dt) {
	  gait_cycle+=dt;

	  Update_Gait_Target(dt,0.5f);

	  float step=( gait_cycle - (float)Math.floor( gait_cycle ) );
	  int leg_to_move=( (int)Math.floor( gait_cycle ) % 2 );

	  // put all feet down except the "active" leg(s).
	  int i;
	  for(i=0;i<6;++i) {
	    if( ( i % 2 ) != leg_to_move ) {
	      legs[ i ].ankle_joint.pos.z=0;
	      continue;
	    }
	    Update_Gait_Leg(i,step,dt);
	  }

	  //Center_Body_Around_Feet(dt);
	  if( paused ) {
	    Plant_Feet();
	  }
	}



	void Move_Apply_Physics(float dt) {
	  int i;

	  //Vector3f gravity=new Vector3f(0,0,-0.00980f*dt);
	  //body.pos+=gravity;

	  for(i=0;i<6;++i) {
	    Leg leg=legs[i];

	    //if(leg.pan_joint.pos  .z>0) leg.pan_joint  .pos += gravity;
	    //if(leg.knee_joint.pos .z>0) leg.knee_joint .pos += gravity;
	    //if(leg.ankle_joint.pos.z>0) leg.ankle_joint.pos += gravity;

	    // keep the joints "legal" (above z=0)
	    // @TODO: rewrite this code when the feet get pressure sensors.
	    if(leg.pan_joint  .pos.z<=0) leg.pan_joint .pos.z = 0;
	    if(leg.knee_joint .pos.z<=0) leg.knee_joint.pos.z = 0;
	    if(leg.ankle_joint.pos.z<=0) {
	      leg.ankle_joint.pos.z = 0;
	      leg.lpoc = leg.ankle_joint.pos;
	      leg.on_ground=true;
	    } else {
	      leg.on_ground=false;
	    }
	  }
	}



	void Move_Apply_Constraints(float dt) {
	  int i;
	  float scale=0.5f;

	  // adjust body orientation
	  body.forward.set( target.forward );
	  body.forward.normalize();
	  body.up.set( target.up );
	  body.up.normalize();
	  body.left.cross( body.up, body.forward );
	  body.forward.cross( body.left, body.up );

	  for(i=0;i<6;++i) {
	    Leg leg=legs[i];
	    // keep shoulders locked in relative position
	    leg.pan_joint.pos.set( body.pos );
	    Vector3f F=new Vector3f(body.forward);
	    Vector3f L=new Vector3f(body.left);
	    Vector3f U=new Vector3f(body.up);
	    F.scale(leg.pan_joint.relative.y);
	    L.scale(leg.pan_joint.relative.x);
	    U.scale(leg.pan_joint.relative.z);
	    leg.pan_joint.pos.add(F);
	    leg.pan_joint.pos.sub(L);
	    leg.pan_joint.pos.add(U);
	    
	    // make sure feet can not come under the body or get too far from the shoulder
	    Vector3f ds = new Vector3f( leg.pan_joint.pos );
	    ds.sub( body.pos );
	    Vector3f df = new Vector3f( leg.ankle_joint.pos );
	    df.sub( body.pos );
	    float dfl=( df.length() );
	    float dsl=( ds.length() );

	    ds.z = 0;
	    ds.normalize();
	    if( dfl < dsl ) {
	      ds.scale(dsl-dfl);
	      leg.ankle_joint.pos.add(ds);
	    } else if( dfl - dsl > max_leg_length ) {
	      // @TODO: should this test should be ankle - pan > max_leg_length ?
	      ds.scale( dfl - dsl - max_leg_length );
	      leg.ankle_joint.pos.sub(ds);
	    }

	    // calculate the pan joint matrix
	    leg.pan_joint.up.set( body.up );
	    leg.pan_joint.forward.set( leg.ankle_joint.pos );
	    leg.pan_joint.forward.sub( leg.pan_joint.pos );
	    
	    df.set(body.up);
	    df.scale( leg.pan_joint.forward.dot( body.up ) );
	    leg.pan_joint.forward.sub(df);
	    
	    if( leg.pan_joint.forward.length() < 0.01f ) {
	      leg.pan_joint.forward.set( leg.tilt_joint.pos );
	      leg.pan_joint.forward.sub( leg.pan_joint.pos );
	    }
	    leg.pan_joint.forward.normalize();
	    leg.pan_joint.left.cross( leg.pan_joint.up, leg.pan_joint.forward );

	    // zero the distance between the pan joint and the tilt joint
	    df.set(leg.pan_joint.forward);
	    df.scale(leg.tilt_joint.relative.length());
	    leg.tilt_joint.pos.set( leg.pan_joint.pos );
	    leg.tilt_joint.pos.add(df); 

	    // zero the knee/foot distance
	    Vector3f a = new Vector3f(leg.knee_joint.pos);
	    a.sub(leg.ankle_joint.pos);
	    float kf = a.length() - leg.ankle_joint.relative.length();
	    if(Math.abs(kf)>0.001) {
	      a.normalize();
	      a.scale(kf*scale);
	      leg.knee_joint.pos.sub(a);
	    }

	    // validate the tilt/knee plane
	    a.set(leg.knee_joint.pos );
	    a.sub( leg.pan_joint.pos );
	    df.set(leg.pan_joint.left);
	    df.scale( a.dot( leg.pan_joint.left ) );
	    leg.knee_joint.pos.sub( df );
	    leg.knee_joint.left.set( leg.pan_joint.left );

	    // zero the tilt/knee distance
	    a.set( leg.knee_joint.pos );
	    a.sub( leg.tilt_joint.pos );
	    float kt = a.length() - leg.knee_joint.relative.length();
	    if(Math.abs(kt)>0.001) {
	      a.normalize();
	      a.scale(kt);
	      // don't push back on the tilt joint, it makes the simulation too unstable.
	      leg.knee_joint.pos.sub(a);
	    }


	    // calculate the tilt joint matrix
	    leg.tilt_joint.left.set( leg.pan_joint.left );
	    a.set( leg.knee_joint.pos );
	    a.sub( leg.tilt_joint.pos );
	    Vector3f b = new Vector3f();
	    b.cross( a, leg.tilt_joint.left );
	    leg.tilt_joint.forward.set(a );
	    leg.tilt_joint.forward.sub( b );
	    leg.tilt_joint.forward.normalize();
	    leg.tilt_joint.up.cross( leg.tilt_joint.forward, leg.tilt_joint.left );

	    // calculate the knee matrix
	    leg.knee_joint.forward.set( leg.ankle_joint.pos );
	    leg.knee_joint.forward.sub( leg.knee_joint.pos );
	    leg.knee_joint.forward.normalize();
	    leg.knee_joint.up.cross( leg.knee_joint.forward, leg.knee_joint.left );
	    //leg.knee_joint.forward = leg.knee_joint.left ^ leg.knee_joint.up;

	    // calculate the ankle matrix
	    leg.ankle_joint.forward.set( leg.knee_joint.forward );
	    leg.ankle_joint.left.set( leg.knee_joint.left );
	    leg.ankle_joint.up.set( leg.knee_joint.up );
	  }
	}



	void Move_Calculate_Angles() {
	  int i,j;
	  float x,y;
	  for(i=0;i<6;++i) {
	    Leg leg=legs[i];

	    // find the pan angle
	    Vector3f sf= new Vector3f( leg.pan_joint.pos );
	    sf.sub( body.pos );
	    sf.normalize();
	    Vector3f sl=new Vector3f();
	    sl.cross( body.up, sf );

	    x = leg.pan_joint.forward.dot(sf);
	    y = leg.pan_joint.forward.dot(sl);
	    float pan_angle = (float)Math.atan2( y, x );

	    // find the tilt angle
	    x = leg.tilt_joint.forward.dot(leg.pan_joint.forward);
	    y = leg.tilt_joint.forward.dot(leg.pan_joint.up     );
	    float tilt_angle = (float)Math.atan2( y, x );

	    // find the knee angle
	    x = leg.knee_joint.forward.dot(leg.tilt_joint.forward);
	    y = leg.knee_joint.forward.dot(leg.tilt_joint.up     );
	    float knee_angle = (float)Math.atan2( y, x );

	    // translate the angles into the servo range, 0...255 over 0...PI.
	    final float scale = ( 255.0f / (float)Math.PI );
	    if( i < 3 ) pan_angle = -pan_angle;
	    float p = leg.pan_joint .zero - pan_angle  * leg.pan_joint .scale * scale;
	    float t = leg.tilt_joint.zero + tilt_angle * leg.tilt_joint.scale * scale;
	    float k = leg.knee_joint.zero - knee_angle * leg.knee_joint.scale * scale;
	    leg.pan_joint .angle = p;
	    leg.tilt_joint.angle = t;
	    leg.knee_joint.angle = k;

	    // record the history for the graphs
	    for( j = 0; j < Joint.ANGLE_HISTORY_LENGTH-1; ++j ) {
	      leg.pan_joint .angle_history[j] = leg.pan_joint .angle_history[j+1];
	      leg.tilt_joint.angle_history[j] = leg.tilt_joint.angle_history[j+1];
	      leg.knee_joint.angle_history[j] = leg.knee_joint.angle_history[j+1];
	    }
	    //memcpy( leg.pan_joint .angle_history, leg.pan_joint .angle_history + sizeof(float), ( Joint.ANGLE_HISTORY_LENGTH - 1 ) * sizeof(float) );
	    //memcpy( leg.tilt_joint.angle_history, leg.tilt_joint.angle_history + sizeof(float), ( Joint.ANGLE_HISTORY_LENGTH - 1 ) * sizeof(float) );
	    //memcpy( leg.knee_joint.angle_history, leg.knee_joint.angle_history + sizeof(float), ( Joint.ANGLE_HISTORY_LENGTH - 1 ) * sizeof(float) );
	    leg.pan_joint .angle_history[ Joint.ANGLE_HISTORY_LENGTH - 1 ] = p - leg.pan_joint .zero;
	    leg.tilt_joint.angle_history[ Joint.ANGLE_HISTORY_LENGTH - 1 ] = t - leg.tilt_joint.zero;
	    leg.knee_joint.angle_history[ Joint.ANGLE_HISTORY_LENGTH - 1 ] = k - leg.knee_joint.zero;

	    // @TODO: contrain angles in the model to the limits set in joint::angle_max and joint::angle_min
	  }
	}


/*
	void Draw2D(GL2 gl2) {
	  int i, j;

	  gl2.glPushMatrix();
	  gl2.glTranslatef( (float)SCREEN_WIDTH / 2 - Joint.ANGLE_HISTORY_LENGTH, (float)SCREEN_HEIGHT / 2 - 100, 0 );
	  for( i = 0; i < 6; ++i ) {
	    Leg leg=legs[i];

	    // baseline
	    gl2.glColor3f( 1, 1, 1 );
	    gl2.glBegin( GL2.GL_LINES );
	    gl2.glVertex2f( 0.0f, -i*100.0f -50.0f );
	    gl2.glVertex2f( (float)Joint.ANGLE_HISTORY_LENGTH, -i*100.0f -50.0f );
	    gl2.glEnd();

	    // pan history
	    gl2.glColor3f( 0, 1, 0 );
	    gl2.glBegin( GL2.GL_LINE_STRIP );
	    for( j = 0; j < Joint.ANGLE_HISTORY_LENGTH; ++j ) {
	      float y = leg.pan_joint.angle_history[ Joint.ANGLE_HISTORY_LENGTH - 1 - j ] / 255.0f;
	      gl2.glVertex2f( (float)j, -i*100.0f -(100.0f - y * 100.0f) );
	    }
	    gl2.glEnd();

	    // tilt history
	    gl2.glColor3f( 1, 0, 0 );
	    gl2.glBegin( GL2.GL_LINE_STRIP );
	    for( j = 0; j < Joint.ANGLE_HISTORY_LENGTH; ++j ) {
	      float y = leg.tilt_joint.angle_history[ Joint.ANGLE_HISTORY_LENGTH - 1 - j ] / 255.0f;
	      gl2.glVertex2f( (float)j, -i*100.0f -(100.0f - y * 100.0f) );
	    }
	    gl2.glEnd();

	    // knee history
	    gl2.glColor3f( 0, 0, 1 );
	    gl2.glBegin( GL2.GL_LINE_STRIP );
	    for( j = 0; j < Joint.ANGLE_HISTORY_LENGTH; ++j ) {
	      float y = leg.knee_joint.angle_history[ Joint.ANGLE_HISTORY_LENGTH - 1 - j ] / 255.0f;
	      gl2.glVertex2f( (float)j, -i*100.0f -(100.0f - y * 100.0f) );
	    }
	    gl2.glEnd();
	  }
	  gl2.glPopMatrix();
	}
	*/
	
	@Override
	// override this method to check that the software is connected to the right type of robot.
	public boolean ConfirmPort(String preamble) {
		if(!portOpened) return false;
		
		if(preamble.contains("Arm3")) {
			portConfirmed=true;			
		}
		return portConfirmed;
	}
}

