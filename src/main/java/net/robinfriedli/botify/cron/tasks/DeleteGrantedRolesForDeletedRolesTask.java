package net.robinfriedli.botify.cron.tasks;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.cron.AbstractCronTask;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.GrantedRole;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.function.modes.HibernateTransactionMode;
import net.robinfriedli.botify.util.StaticSessionProvider;
import net.robinfriedli.exec.Mode;
import org.quartz.JobExecutionContext;

/**
 * Deletes {@link GrantedRole} entities for Roles that have been deleted
 */
public class DeleteGrantedRolesForDeletedRolesTask extends AbstractCronTask {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    protected void run(JobExecutionContext jobExecutionContext) {
        ShardManager shardManager = Botify.get().getShardManager();
        StaticSessionProvider.consumeSession(session -> {
            List<GuildSpecification> guildSpecifications = session.createQuery("from " + GuildSpecification.class.getName(), GuildSpecification.class).getResultList();
            int deletionCounter = 0;

            for (GuildSpecification guildSpecification : guildSpecifications) {
                Guild guild = guildSpecification.getGuild(shardManager);

                if (guild == null) {
                    continue;
                }

                for (AccessConfiguration accessConfiguration : guildSpecification.getAccessConfigurations()) {
                    Set<GrantedRole> grantedRoles = Sets.newHashSet(accessConfiguration.getRoles());
                    for (GrantedRole grantedRole : grantedRoles) {
                        Role guildRole = grantedRole.getRole(guild);

                        if (guildRole == null) {
                            // role has been deleted
                            accessConfiguration.removeRole(grantedRole);
                            session.delete(grantedRole);
                            ++deletionCounter;
                        }
                    }
                }
            }

            if (deletionCounter > 0) {
                logger.info("Deleted " + deletionCounter + " GrantedRole entities for roles that no longer exist");
            }
        });
    }

    @Override
    protected Mode getMode() {
        return Mode.create().with(new HibernateTransactionMode());
    }
}
